package br.com.turnoseguro.app;

import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class InspectionStore {
    static final String BLOCKED = "bloqueada";
    static final String RELEASED = "liberada";

    enum Result { BLOCKED, RELEASED, ALREADY_BLOCKED, MODEL_CHANGED, RELEASE_CHANGED }

    private final FirebaseFirestore db;

    InspectionStore(FirebaseFirestore db) {
        this.db = db;
    }

    Task<Result> send(String machineId, String type, String uid, String email, String inspectionId,
                      List<?> shownItems, List<Map<String, Object>> answers, boolean criticalFailure) {
        var machine = db.collection("maquinas").document(machineId);
        var model = db.collection("modelos").document(type);
        var inspection = db.collection("inspecoes").document(inspectionId);
        String status = criticalFailure ? BLOCKED : RELEASED;
        return db.runTransaction(transaction -> {
            var previous = transaction.get(inspection);
            if (previous.exists()) {
                return BLOCKED.equals(previous.get("resultado")) ? Result.BLOCKED : Result.RELEASED;
            }
            var current = transaction.get(machine);
            if (BLOCKED.equals(current.get("status"))) {
                return Result.ALREADY_BLOCKED;
            }
            if (!type.equals(current.get("tipo"))
                    || !shownItems.equals(transaction.get(model).get("itens"))) {
                return Result.MODEL_CHANGED;
            }
            Map<String, Object> data = new HashMap<>();
            data.put("maquinaId", machineId);
            data.put("tipo", type);
            data.put("operadorUid", uid);
            data.put("operadorEmail", email);
            data.put("itens", shownItems);
            data.put("respostas", answers);
            data.put("resultado", status);
            data.put("data", FieldValue.serverTimestamp());
            transaction.set(inspection, data);
            transaction.update(machine, "status", status, "ultimaInspecaoId", inspectionId,
                    "ultimaInspecaoEm", FieldValue.serverTimestamp());
            return criticalFailure ? Result.BLOCKED : Result.RELEASED;
        });
    }

    Task<Result> release(String machineId, String blockedInspectionId, String uid, String releaseId,
                         String justification) {
        var machine = db.collection("maquinas").document(machineId);
        var release = db.collection("liberacoes").document(releaseId);
        return db.runTransaction(transaction -> {
            if (transaction.get(release).exists()) {
                return Result.RELEASED;
            }
            var current = transaction.get(machine);
            if (!BLOCKED.equals(current.get("status"))
                    || !blockedInspectionId.equals(current.get("ultimaInspecaoId"))) {
                return Result.RELEASE_CHANGED;
            }
            Map<String, Object> data = new HashMap<>();
            data.put("maquinaId", machineId);
            data.put("inspecaoId", blockedInspectionId);
            data.put("supervisorUid", uid);
            data.put("justificativa", justification);
            data.put("data", FieldValue.serverTimestamp());
            transaction.set(release, data);
            transaction.update(machine, "status", RELEASED, "ultimaLiberacaoId", releaseId);
            return Result.RELEASED;
        });
    }
}
