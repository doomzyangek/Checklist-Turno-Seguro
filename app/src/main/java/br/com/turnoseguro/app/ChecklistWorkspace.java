package br.com.turnoseguro.app;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.Source;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class ChecklistWorkspace {
    private static final int MAX_ITEMS = 10;
    private static final int HISTORY_PAGE_SIZE = 30;
    private static final ZoneId FACTORY_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter HISTORY_DATE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm").withZone(FACTORY_ZONE);
    private static final String TYPE_PATTERN = "[a-z][a-z0-9-]{0,39}";

    private record ModelRow(EditText text, CheckBox critical, Button remove) {}
    private record AnswerRow(RadioGroup options, EditText observation, boolean critical) {}

    private final Handler clock = new Handler(Looper.getMainLooper());
    private final Runnable refreshDay = () -> showMachines();
    private final MainActivity activity;
    private final LinearLayout panel;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final InspectionStore inspections = new InspectionStore(db);
    private final ArrayList<ModelRow> modelRows = new ArrayList<>();
    private final ArrayList<AnswerRow> answerRows = new ArrayList<>();

    private Bundle draft;
    private String uid;
    private String role;
    private String machineId;
    private String checklistType;
    private boolean machineList;
    private boolean historyScreen;
    private int historyLimit = HISTORY_PAGE_SIZE;
    private ListenerRegistration machineDetailsListener;
    private String operationId;
    private EditText justification;
    private String blockedInspectionId;
    private ListenerRegistration machinesListener;
    private EditText modelType;
    private int page;

    ChecklistWorkspace(MainActivity activity, LinearLayout panel, Bundle draft) {
        this.activity = activity;
        this.panel = panel;
        this.draft = draft;
    }

    void show(String userId, String profile) {
        if (!userId.equals(uid) || !profile.equals(role)) {
            Bundle restored = draft;
            clear();
            uid = userId;
            role = profile;
            if (restored != null && userId.equals(restored.getString("uid"))
                    && profile.equals(restored.getString("role"))) {
                draft = restored;
            }
        }
        panel.setVisibility(View.VISIBLE);
        if (panel.getChildCount() != 0) {
            if (machinesListener == null && historyScreen) {
                showHistory(machineId, historyLimit);
            } else if (machinesListener == null && machineList) {
                showMachines();
            }
            return;
        }
        if ("supervisor".equals(role)) {
            String restoredMachine = draft == null ? null : draft.getString("machine");
            if (restoredMachine != null && draft.getBoolean("historyScreen")) {
                showHistory(restoredMachine, draft.getInt("historyLimit", HISTORY_PAGE_SIZE));
            } else if (restoredMachine != null) {
                showRelease(restoredMachine, draft);
            } else if (draft != null && draft.getStringArrayList("items") != null) {
                showModelEditor(draft);
            } else {
                showMachines();
            }
        } else if (draft != null && draft.getString("machine") != null) {
            loadChecklist(draft.getString("machine"), draft);
        } else {
            showMachines();
        }
    }

    void pause() {
        stopMachines();
        panel.setVisibility(View.GONE);
    }

    void clear() {
        pause();
        uid = null;
        role = null;
        machineId = null;
        draft = null;
        resetPanel();
    }

    Bundle save() {
        if (uid == null) {
            return draft;
        }
        Bundle state = draft == null ? new Bundle() : new Bundle(draft);
        state.putString("uid", uid);
        state.putString("role", role);
        state.putString("machine", machineId);
        state.putBoolean("historyScreen", historyScreen);
        state.putInt("historyLimit", historyLimit);
        state.putString("operationId", operationId);
        if (justification != null) {
            state.putString("justification", justification.getText().toString());
            state.putString("blockedInspectionId", blockedInspectionId);
        }
        if (checklistType != null) {
            state.putString("checklistType", checklistType);
        }
        if (modelType != null) {
            state.putString("type", modelType.getText().toString());
            ArrayList<String> texts = new ArrayList<>();
            boolean[] critical = new boolean[modelRows.size()];
            for (int i = 0; i < modelRows.size(); i++) {
                ModelRow row = modelRows.get(i);
                texts.add(row.text().getText().toString());
                critical[i] = row.critical().isChecked();
            }
            state.putStringArrayList("items", texts);
            state.putBooleanArray("critical", critical);
        }
        if (!answerRows.isEmpty()) {
            int[] choices = new int[answerRows.size()];
            ArrayList<String> notes = new ArrayList<>();
            for (int i = 0; i < answerRows.size(); i++) {
                AnswerRow row = answerRows.get(i);
                choices[i] = row.options().indexOfChild(
                        row.options().findViewById(row.options().getCheckedRadioButtonId()));
                notes.add(row.observation().getText().toString());
            }
            state.putIntArray("choices", choices);
            state.putStringArrayList("notes", notes);
        }
        return state;
    }

    private TextView showModelEditor(Bundle restored) {
        stopMachines();
        resetPanel();
        draft = null;
        machineId = null;
        button(panel, "Voltar para o painel").setOnClickListener(view -> showMachines());
        heading("Cadastrar modelo");
        modelType = field(panel, "Código do tipo (ex.: prensa)", 40, true);
        if (restored != null) {
            modelType.setText(restored.getString("type"));
        }
        LinearLayout items = new LinearLayout(activity);
        items.setOrientation(LinearLayout.VERTICAL);
        panel.addView(items);
        Button add = button(panel, "Adicionar item");
        TextView message = message();
        Button save = button(panel, "Salvar modelo");

        add.setOnClickListener(view -> addModelRow(items, add, "", false));
        ArrayList<String> texts = restored == null ? null : restored.getStringArrayList("items");
        boolean[] critical = restored == null ? null : restored.getBooleanArray("critical");
        if (texts == null || texts.isEmpty()) {
            addModelRow(items, add, "", false);
        } else {
            for (int i = 0; i < texts.size(); i++) {
                addModelRow(items, add, texts.get(i), critical != null && critical[i]);
            }
        }

        save.setOnClickListener(view -> {
            message.setText("");
            String type = modelType.getText().toString().trim();
            if (!type.matches(TYPE_PATTERN)) {
                modelType.setError("Comece com uma letra minúscula e use apenas letras minúsculas, números e hífen.");
                modelType.requestFocus();
                return;
            }
            ArrayList<Map<String, Object>> entries = new ArrayList<>();
            for (ModelRow row : modelRows) {
                String text = row.text().getText().toString().trim();
                if (text.isEmpty()) {
                    row.text().setError("Descreva o item.");
                    row.text().requestFocus();
                    return;
                }
                Map<String, Object> entry = new HashMap<>();
                entry.put("texto", text);
                entry.put("critico", row.critical().isChecked());
                entries.add(entry);
            }

            int requestPage = page;
            var reference = db.collection("modelos").document(type);
            setEditorEnabled(add, save, false);
            message.setText("Salvando modelo…");
            db.runTransaction(transaction -> {
                if (transaction.get(reference).exists()) {
                    return false;
                }
                transaction.set(reference, Collections.singletonMap("itens", entries));
                return true;
            }).addOnCompleteListener(task -> {
                if (requestPage != page || activity.isDestroyed()) {
                    return;
                }
                setEditorEnabled(add, save, true);
                if (!task.isSuccessful()) {
                    message.setText("Não foi possível salvar. Confira a conexão e as permissões.");
                } else if (Boolean.TRUE.equals(task.getResult())) {
                    showModelEditor(null).setText("Modelo salvo: " + type);
                } else {
                    message.setText("Já existe um modelo para esse tipo.");
                }
            });
        });
        return message;
    }

    private void setEditorEnabled(Button add, Button save, boolean enabled) {
        modelType.setEnabled(enabled);
        add.setEnabled(enabled && modelRows.size() < MAX_ITEMS);
        save.setEnabled(enabled);
        for (ModelRow row : modelRows) {
            row.text().setEnabled(enabled);
            row.critical().setEnabled(enabled);
            row.remove().setEnabled(enabled);
        }
    }

    private void addModelRow(LinearLayout items, Button add, String value, boolean checked) {
        if (modelRows.size() == MAX_ITEMS) {
            return;
        }
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.VERTICAL);
        EditText text = field(row, "Descrição do item", 160, true);
        text.setText(value);
        CheckBox critical = new CheckBox(activity);
        critical.setText("Item crítico");
        critical.setChecked(checked);
        row.addView(critical);
        Button remove = button(row, "Remover item");
        ModelRow item = new ModelRow(text, critical, remove);
        remove.setOnClickListener(view -> {
            if (modelRows.size() == 1) {
                text.setError("O modelo precisa ter pelo menos um item.");
                return;
            }
            items.removeView(row);
            modelRows.remove(item);
            add.setEnabled(true);
        });
        modelRows.add(item);
        items.addView(row);
        add.setEnabled(modelRows.size() < MAX_ITEMS);
    }

    private void showMachines() {
        showMachines("");
    }

    private void showMachines(String feedback) {
        stopMachines();
        resetPanel();
        machineId = null;
        draft = null;
        machineList = true;
        boolean supervisor = "supervisor".equals(role);
        if (supervisor) {
            button(panel, "Cadastrar modelo").setOnClickListener(view -> showModelEditor(null));
        }
        heading(supervisor ? "Situação das máquinas hoje" : "Escolha uma máquina");
        if (supervisor) {
            message().setText("Selecione uma máquina para ver o histórico.");
        }
        if (!feedback.isEmpty()) {
            message().setText(feedback);
        }
        TextView message = message();
        message.setText("Carregando máquinas…");
        RecyclerView list = new RecyclerView(activity);
        list.setLayoutManager(new LinearLayoutManager(activity));
        list.setNestedScrollingEnabled(false);
        panel.addView(list, new LinearLayout.LayoutParams(-1, -2));
        if (panel.getVisibility() != View.VISIBLE) {
            return;
        }
        if (supervisor) {
            ZonedDateTime now = ZonedDateTime.now(FACTORY_ZONE);
            ZonedDateTime midnight = now.toLocalDate().plusDays(1).atStartOfDay(FACTORY_ZONE);
            clock.postDelayed(refreshDay, Duration.between(now, midnight).toMillis() + 100);
        }
        int requestPage = page;
        machinesListener = db.collection("maquinas").orderBy("nome")
                .addSnapshotListener(MetadataChanges.INCLUDE, (snapshot, error) -> {
                    if (requestPage != page || machinesListener == null) {
                        return;
                    }
                    if (error != null || snapshot == null) {
                        message.setText("Não foi possível carregar as máquinas.");
                        list.setAdapter(null);
                        return;
                    }
                    message.setText(snapshot.getMetadata().isFromCache()
                            ? "Aguardando dados atualizados do Firestore…"
                            : snapshot.isEmpty() ? "Nenhuma máquina cadastrada." : "");
                    if (snapshot.getMetadata().isFromCache()) {
                        list.setAdapter(null);
                        return;
                    }
                    list.setAdapter(new MachineAdapter(snapshot.getDocuments()));
                });
    }

    private void loadChecklist(String id, Bundle restored) {
        stopMachines();
        resetPanel();
        machineId = id;
        draft = restored;
        operationId = restored == null ? null : restored.getString("operationId");
        button(panel, "Voltar para máquinas").setOnClickListener(view -> showMachines());
        TextView message = message();
        message.setText("Carregando checklist…");
        int requestPage = page;
        db.collection("maquinas").document(id).get(Source.SERVER).addOnCompleteListener(machineTask -> {
            if (requestPage != page || activity.isDestroyed()) {
                return;
            }
            DocumentSnapshot machine = machineTask.isSuccessful() ? machineTask.getResult() : null;
            if (machine == null || !(machine.get("tipo") instanceof String type)
                    || !type.matches(TYPE_PATTERN)) {
                showLoadError(message, id, restored, "Máquina indisponível. Confira o cadastro e a conexão.");
                return;
            }
            if (InspectionStore.BLOCKED.equals(machine.get("status")) && operationId == null) {
                message.setText("Máquina bloqueada. Solicite a liberação ao supervisor.");
                return;
            }
            db.collection("modelos").document(type).get(Source.SERVER).addOnCompleteListener(modelTask -> {
                if (requestPage != page || activity.isDestroyed()) {
                    return;
                }
                if (!modelTask.isSuccessful()) {
                    showLoadError(message, id, restored, "Não foi possível carregar o modelo.");
                    return;
                }
                var model = modelTask.getResult();
                if (!(model.get("itens") instanceof List<?> items) || items.isEmpty()) {
                    message.setText("Esta máquina ainda não tem um modelo de checklist.");
                    return;
                }
                message.setText("");
                Object name = machine.get("nome");
                heading(name instanceof String label ? label : id);
                checklistType = type;
                Bundle matchingDraft = restored != null && type.equals(restored.getString("checklistType"))
                        ? restored : null;
                for (int i = 0; i < items.size(); i++) {
                    if (!(items.get(i) instanceof Map<?, ?> item)
                            || !(item.get("texto") instanceof String text)
                            || !(item.get("critico") instanceof Boolean critical)) {
                        showLoadError(message, id, restored, "O modelo está incompleto.");
                        return;
                    }
                    addAnswer(text, critical, i, matchingDraft);
                }
                TextView validation = message();
                Button send = button(panel, "Enviar inspeção");
                send.setOnClickListener(view -> sendInspection(send, validation, items));
            });
        });
    }

    private void showLoadError(TextView message, String id, Bundle restored, String text) {
        message.setText(text);
        button(panel, "Tentar novamente").setOnClickListener(view -> loadChecklist(id, restored));
    }

    private void addAnswer(String text, boolean critical, int index, Bundle restored) {
        heading((index + 1) + ". " + text + (critical ? " · Crítico" : ""));
        RadioGroup options = new RadioGroup(activity);
        for (String label : new String[]{"Conforme", "Não conforme"}) {
            RadioButton option = new RadioButton(activity);
            option.setId(View.generateViewId());
            option.setText(label);
            options.addView(option);
        }
        panel.addView(options);
        EditText note = field(panel, "Observação obrigatória", 500, false);
        note.setVisibility(View.GONE);
        options.setOnCheckedChangeListener((group, selected) -> {
            boolean nonconforming = selected == group.getChildAt(1).getId();
            note.setVisibility(nonconforming ? View.VISIBLE : View.GONE);
            note.setError(null);
        });
        answerRows.add(new AnswerRow(options, note, critical));
        int[] choices = restored == null ? null : restored.getIntArray("choices");
        ArrayList<String> notes = restored == null ? null : restored.getStringArrayList("notes");
        if (choices != null && index < choices.length && choices[index] >= 0) {
            options.check(options.getChildAt(choices[index]).getId());
        }
        if (notes != null && index < notes.size()) {
            note.setText(notes.get(index));
        }
    }

    private void sendInspection(Button send, TextView validation, List<?> items) {
        List<Map<String, Object>> answers = new ArrayList<>();
        boolean criticalFailure = false;
        for (int i = 0; i < answerRows.size(); i++) {
            AnswerRow row = answerRows.get(i);
            int selected = row.options().getCheckedRadioButtonId();
            if (selected == -1) {
                validation.setText("Responda ao item " + (i + 1) + ".");
                row.options().getChildAt(0).requestFocus();
                return;
            }
            boolean conforming = selected == row.options().getChildAt(0).getId();
            String note = conforming ? "" : row.observation().getText().toString().trim();
            if (!conforming && note.isEmpty()) {
                row.observation().setError("Explique a não conformidade.");
                row.observation().requestFocus();
                validation.setText("Informe uma observação no item " + (i + 1) + ".");
                return;
            }
            criticalFailure |= !conforming && row.critical();
            Map<String, Object> answer = new HashMap<>();
            answer.put("conforme", conforming);
            answer.put("observacao", note);
            answers.add(answer);
        }
        var user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || !uid.equals(user.getUid()) || user.getEmail() == null) {
            validation.setText("Entre novamente para enviar a inspeção.");
            return;
        }
        if (operationId == null) {
            operationId = db.collection("inspecoes").document().getId();
        }
        int requestPage = page;
        send.setEnabled(false);
        validation.setText("Enviando inspeção…");
        inspections.send(machineId, checklistType, uid, user.getEmail(), operationId, items, answers,
                criticalFailure).addOnCompleteListener(task -> {
                    if (requestPage != page || activity.isDestroyed()) {
                        return;
                    }
                    if (!task.isSuccessful()) {
                        send.setEnabled(true);
                        validation.setText("Não foi possível confirmar o envio. Confira a conexão e tente novamente.");
                        return;
                    }
                    switch (task.getResult()) {
                        case BLOCKED -> showMachines("Inspeção registrada. Máquina bloqueada.");
                        case RELEASED -> showMachines("Inspeção registrada. Máquina liberada.");
                        case ALREADY_BLOCKED -> validation.setText("A máquina já está bloqueada. Aguarde a liberação.");
                        case MODEL_CHANGED -> validation.setText("O modelo mudou. Volte e abra o checklist novamente.");
                        default -> throw new IllegalStateException("Resultado inesperado");
                    }
                });
    }

    private void showHistory(String id, int limit) {
        stopMachines();
        resetPanel();
        draft = null;
        machineId = id;
        historyScreen = true;
        historyLimit = limit;
        button(panel, "Voltar para o painel").setOnClickListener(view -> showMachines());
        TextView title = heading("Histórico da máquina");
        Button unlock = button(panel, "Desbloquear máquina");
        unlock.setVisibility(View.GONE);
        unlock.setOnClickListener(view -> showRelease(id, null));

        TextView status = message();
        status.setText("Carregando inspeções…");
        RecyclerView list = new RecyclerView(activity);
        list.setLayoutManager(new LinearLayoutManager(activity));
        list.setNestedScrollingEnabled(false);
        panel.addView(list, new LinearLayout.LayoutParams(-1, -2));
        Button more = button(panel, "Mostrar mais inspeções");
        more.setVisibility(View.GONE);
        more.setOnClickListener(view -> showHistory(id, limit + HISTORY_PAGE_SIZE));
        if (panel.getVisibility() != View.VISIBLE) {
            return;
        }

        int requestPage = page;
        machineDetailsListener = db.collection("maquinas").document(id)
                .addSnapshotListener(MetadataChanges.INCLUDE, (machine, error) -> {
                    if (requestPage != page || machineDetailsListener == null) {
                        return;
                    }
                    boolean current = error == null && machine != null && machine.exists()
                            && !machine.getMetadata().isFromCache();
                    Object name = current ? machine.get("nome") : null;
                    title.setText("Histórico · " + (name instanceof String label ? label : id));
                    boolean blocked = current && InspectionStore.BLOCKED.equals(machine.get("status"));
                    unlock.setVisibility(blocked ? View.VISIBLE : View.GONE);
                });
        machinesListener = db.collection("inspecoes")
                .whereEqualTo("maquinaId", id)
                .orderBy("data", Query.Direction.DESCENDING)
                .limit((long) limit + 1)
                .addSnapshotListener(MetadataChanges.INCLUDE, (snapshot, error) -> {
                    if (requestPage != page || machinesListener == null) {
                        return;
                    }
                    boolean ready = error == null && snapshot != null
                            && !snapshot.getMetadata().isFromCache();
                    if (!ready) {
                        status.setText(error != null ? "Não foi possível carregar o histórico."
                                : "Aguardando dados atualizados do Firestore…");
                        list.setAdapter(null);
                        more.setVisibility(View.GONE);
                        return;
                    }
                    List<DocumentSnapshot> rows = snapshot.getDocuments();
                    status.setText(rows.isEmpty() ? "Nenhuma inspeção registrada para esta máquina." : "");
                    list.setAdapter(new HistoryAdapter(rows.subList(0, Math.min(limit, rows.size()))));
                    more.setVisibility(rows.size() > limit ? View.VISIBLE : View.GONE);
                });
    }

    private void showRelease(String id, Bundle restored) {
        stopMachines();
        resetPanel();
        machineId = id;
        draft = restored;
        operationId = restored == null ? null : restored.getString("operationId");
        button(panel, "Voltar para o histórico").setOnClickListener(view -> showHistory(id, HISTORY_PAGE_SIZE));
        TextView status = message();
        status.setText("Carregando máquina…");
        int requestPage = page;
        db.collection("maquinas").document(id).get(Source.SERVER).addOnCompleteListener(task -> {
            if (requestPage != page || activity.isDestroyed()) {
                return;
            }
            if (!task.isSuccessful()) {
                status.setText("Não foi possível consultar a máquina.");
                button(panel, "Tentar novamente").setOnClickListener(view -> showRelease(id, restored));
                return;
            }
            DocumentSnapshot machine = task.getResult();
            if (!InspectionStore.BLOCKED.equals(machine.get("status"))
                    || !(machine.get("ultimaInspecaoId") instanceof String inspectionId)) {
                status.setText("Esta máquina já foi liberada ou está sem registro de bloqueio.");
                return;
            }
            if (restored != null && !inspectionId.equals(restored.getString("blockedInspectionId"))) {
                operationId = null;
            }
            blockedInspectionId = inspectionId;
            status.setText("");
            Object name = machine.get("nome");
            heading("Desbloquear " + (name instanceof String label ? label : id));
            justification = field(panel, "Justificativa obrigatória", 500, false);
            if (restored != null && inspectionId.equals(restored.getString("blockedInspectionId"))) {
                justification.setText(restored.getString("justification"));
            }
            Button release = button(panel, "Confirmar desbloqueio");
            release.setOnClickListener(view -> releaseMachine(release, status));
        });
    }

    private void releaseMachine(Button release, TextView status) {
        String reason = justification.getText().toString().trim();
        if (reason.isEmpty()) {
            justification.setError("Informe a justificativa.");
            justification.requestFocus();
            return;
        }
        if (operationId == null) {
            operationId = db.collection("liberacoes").document().getId();
        }
        int requestPage = page;
        release.setEnabled(false);
        status.setText("Registrando liberação…");
        inspections.release(machineId, blockedInspectionId, uid, operationId, reason)
                .addOnCompleteListener(task -> {
                    if (requestPage != page || activity.isDestroyed()) {
                        return;
                    }
                    if (!task.isSuccessful()) {
                        release.setEnabled(true);
                        status.setText("Não foi possível confirmar a liberação. Tente novamente.");
                    } else if (task.getResult() == InspectionStore.Result.RELEASED) {
                        showMachines("Máquina liberada. Justificativa registrada.");
                    } else {
                        status.setText("O bloqueio mudou. Volte à lista e consulte a máquina novamente.");
                    }
                });
    }

    private void resetPanel() {
        page++;
        panel.removeAllViews();
        ScrollView screen = activity.findViewById(R.id.screen_root);
        screen.scrollTo(0, 0);
        modelType = null;
        checklistType = null;
        machineList = false;
        historyScreen = false;
        historyLimit = HISTORY_PAGE_SIZE;
        operationId = null;
        justification = null;
        blockedInspectionId = null;
        modelRows.clear();
        answerRows.clear();
    }

    private void stopMachines() {
        clock.removeCallbacks(refreshDay);
        if (machineDetailsListener != null) {
            machineDetailsListener.remove();
            machineDetailsListener = null;
        }
        if (machinesListener != null) {
            machinesListener.remove();
            machinesListener = null;
        }
    }

    private TextView heading(String text) {
        TextView heading = message();
        heading.setText(text);
        heading.setTextSize(19);
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            heading.setAccessibilityHeading(true);
        }
        return heading;
    }

    private TextView message() {
        TextView view = new TextView(activity);
        view.setTextColor(activity.getColor(R.color.graphite));
        view.setTextSize(16);
        view.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        panel.addView(view);
        return view;
    }

    private Button button(ViewGroup parent, String text) {
        Button button = new Button(activity);
        button.setText(text);
        button.setAllCaps(false);
        button.setBackgroundTintList(ColorStateList.valueOf(activity.getColor(R.color.industrial_blue)));
        button.setTextColor(activity.getColor(R.color.surface));
        parent.addView(button, new ViewGroup.LayoutParams(-1, -2));
        return button;
    }

    private EditText field(ViewGroup parent, String hint, int limit, boolean singleLine) {
        EditText field = new EditText(activity);
        field.setHint(hint);
        field.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | (singleLine ? 0 : android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE));
        field.setSingleLine(singleLine);
        field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(limit)});
        parent.addView(field, new ViewGroup.LayoutParams(-1, -2));
        return field;
    }

    private static String dailyStatus(DocumentSnapshot machine) {
        if (InspectionStore.BLOCKED.equals(machine.get("status"))) {
            return "Bloqueada";
        }
        Object inspectedAt = machine.get("ultimaInspecaoEm");
        if (InspectionStore.RELEASED.equals(machine.get("status"))
                && inspectedAt instanceof Timestamp time
                && LocalDate.now(FACTORY_ZONE).equals(time.toDate().toInstant().atZone(FACTORY_ZONE).toLocalDate())) {
            return "Liberada";
        }
        return "Sem inspeção";
    }

    private final class HistoryAdapter extends RecyclerView.Adapter<HistoryHolder> {
        private final List<DocumentSnapshot> rows;

        HistoryAdapter(List<DocumentSnapshot> rows) {
            this.rows = rows;
        }

        @Override
        public HistoryHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            TextView row = new TextView(parent.getContext());
            row.setTextColor(activity.getColor(R.color.graphite));
            row.setTextSize(16);
            row.setMinHeight((int) (72 * row.getResources().getDisplayMetrics().density));
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
            return new HistoryHolder(row);
        }

        @Override
        public void onBindViewHolder(HistoryHolder holder, int position) {
            DocumentSnapshot inspection = rows.get(position);
            Object email = inspection.get("operadorEmail");
            Object uid = inspection.get("operadorUid");
            Object date = inspection.get("data");
            Object result = inspection.get("resultado");
            String operator = email instanceof String address && !address.isEmpty()
                    ? address : uid instanceof String id ? id : "Operador desconhecido";
            String when = date instanceof Timestamp time
                    ? HISTORY_DATE.format(time.toDate().toInstant()) : "Data indisponível";
            String outcome = InspectionStore.BLOCKED.equals(result) ? "Bloqueada"
                    : InspectionStore.RELEASED.equals(result) ? "Liberada" : "Resultado indisponível";
            holder.row.setText(operator + "\n" + when + " · " + outcome);
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }
    }

    private static final class HistoryHolder extends RecyclerView.ViewHolder {
        private final TextView row;

        HistoryHolder(TextView row) {
            super(row);
            this.row = row;
        }
    }

    private final class MachineAdapter extends RecyclerView.Adapter<MachineHolder> {
        private final List<DocumentSnapshot> machines;

        MachineAdapter(List<DocumentSnapshot> machines) {
            this.machines = machines;
        }

        @Override
        public MachineHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            Button button = new Button(parent.getContext());
            button.setAllCaps(false);
            button.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
            return new MachineHolder(button);
        }

        @Override
        public void onBindViewHolder(MachineHolder holder, int position) {
            DocumentSnapshot machine = machines.get(position);
            Object name = machine.get("nome");
            String label = name instanceof String text ? text : machine.getId();
            boolean blocked = InspectionStore.BLOCKED.equals(machine.get("status"));
            boolean supervisor = "supervisor".equals(role);
            String status = dailyStatus(machine);
            holder.button.setText(supervisor ? label + " · " + status
                    : blocked ? label + " · Bloqueada" : label);
            int background = blocked ? R.color.blocked_red
                    : supervisor && "Liberada".equals(status) ? R.color.released_green
                    : R.color.surface;
            int foreground = background == R.color.surface ? R.color.graphite : R.color.surface;
            holder.button.setBackgroundTintList(ColorStateList.valueOf(activity.getColor(background)));
            holder.button.setTextColor(activity.getColor(foreground));
            holder.button.setOnClickListener(view -> {
                if (supervisor) {
                    showHistory(machine.getId(), HISTORY_PAGE_SIZE);
                } else {
                    loadChecklist(machine.getId(), null);
                }
            });
        }

        @Override
        public int getItemCount() {
            return machines.size();
        }
    }

    private static final class MachineHolder extends RecyclerView.ViewHolder {
        private final Button button;

        MachineHolder(Button button) {
            super(button);
            this.button = button;
        }
    }
}
