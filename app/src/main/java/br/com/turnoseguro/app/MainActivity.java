package br.com.turnoseguro.app;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;

public final class MainActivity extends Activity {
    private final FirebaseAuth.AuthStateListener authListener = this::showSession;
    private FirebaseAuth auth;
    private ListenerRegistration profileListener;
    private int profileGeneration;
    private boolean registering;
    private boolean submitting;
    private View authForm;
    private View account;
    private EditText email;
    private EditText password;
    private TextView formTitle;
    private TextView formMessage;
    private TextView accountTitle;
    private TextView accountMessage;
    private Button submit;
    private Button switchMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        applyWindowInsets(findViewById(R.id.screen_root));

        auth = FirebaseAuth.getInstance();
        authForm = findViewById(R.id.auth_form);
        account = findViewById(R.id.account);
        email = findViewById(R.id.email);
        password = findViewById(R.id.password);
        formTitle = findViewById(R.id.form_title);
        formMessage = findViewById(R.id.form_message);
        accountTitle = findViewById(R.id.account_title);
        accountMessage = findViewById(R.id.account_message);
        submit = findViewById(R.id.submit);
        switchMode = findViewById(R.id.switch_mode);

        registering = savedInstanceState != null && savedInstanceState.getBoolean("registering");
        if (getLastNonConfigurationInstance() instanceof String draft) {
            password.setText(draft);
        }
        updateForm();
        submit.setOnClickListener(view -> submitCredentials());
        switchMode.setOnClickListener(view -> {
            registering = !registering;
            formMessage.setText(null);
            email.setError(null);
            password.setError(null);
            updateForm();
        });
        findViewById(R.id.sign_out).setOnClickListener(view -> auth.signOut());
    }

    @Override
    protected void onStart() {
        super.onStart();
        auth.addAuthStateListener(authListener);
    }

    @Override
    protected void onStop() {
        auth.removeAuthStateListener(authListener);
        stopProfileListener();
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putBoolean("registering", registering);
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        return password.getText().toString();
    }

    private void updateForm() {
        formTitle.setText(registering ? R.string.create_account : R.string.sign_in);
        submit.setText(registering ? R.string.create_account : R.string.sign_in);
        switchMode.setText(registering ? R.string.back_to_sign_in : R.string.create_account);
    }

    private void submitCredentials() {
        if (submitting) {
            return;
        }
        String address = email.getText().toString().trim();
        String secret = password.getText().toString();
        formMessage.setText(null);

        if (!Patterns.EMAIL_ADDRESS.matcher(address).matches()) {
            email.setError(getString(R.string.invalid_email));
            email.requestFocus();
            return;
        }
        if (secret.isEmpty() || (registering && secret.length() < 6)) {
            password.setError(getString(registering ? R.string.short_password : R.string.missing_password));
            password.requestFocus();
            return;
        }

        submitting = true;
        submit.setEnabled(false);
        switchMode.setEnabled(false);
        var request = registering
                ? auth.createUserWithEmailAndPassword(address, secret)
                : auth.signInWithEmailAndPassword(address, secret);
        request.addOnCompleteListener(result -> {
            if (isDestroyed()) {
                return;
            }
            submitting = false;
            submit.setEnabled(true);
            switchMode.setEnabled(true);
            if (!result.isSuccessful()) {
                formMessage.setText(authError(result.getException()));
            }
        });
    }

    private int authError(Exception error) {
        if (error instanceof FirebaseNetworkException) {
            return R.string.network_error;
        }
        if (error instanceof FirebaseAuthWeakPasswordException) {
            return R.string.weak_password;
        }
        if (error instanceof FirebaseAuthUserCollisionException) {
            return R.string.account_exists;
        }
        return registering ? R.string.registration_failed : R.string.sign_in_failed;
    }

    private void showSession(FirebaseAuth firebaseAuth) {
        stopProfileListener();
        FirebaseUser user = firebaseAuth.getCurrentUser();
        authForm.setVisibility(user == null ? View.VISIBLE : View.GONE);
        account.setVisibility(user == null ? View.GONE : View.VISIBLE);
        if (user == null) {
            return;
        }

        password.setText(null);
        formMessage.setText(null);
        registering = false;
        updateForm();
        accountTitle.setText(R.string.account_title);
        accountMessage.setText(R.string.loading_profile);

        int generation = profileGeneration;
        profileListener = FirebaseFirestore.getInstance()
                .collection("usuarios")
                .document(user.getUid())
                .addSnapshotListener(MetadataChanges.INCLUDE, (document, error) -> {
                    if (generation != profileGeneration) {
                        return;
                    }
                    accountTitle.setText(R.string.account_title);
                    if (error != null || document == null || document.getMetadata().isFromCache()) {
                        accountMessage.setText(R.string.profile_unavailable);
                        return;
                    }
                    Object profile = document.get("perfil");
                    if ("operador".equals(profile)) {
                        accountTitle.setText(R.string.operator);
                        accountMessage.setText(user.getEmail());
                    } else if ("supervisor".equals(profile)) {
                        accountTitle.setText(R.string.supervisor);
                        accountMessage.setText(user.getEmail());
                    } else {
                        accountMessage.setText(R.string.profile_pending);
                    }
                });
    }

    private void stopProfileListener() {
        profileGeneration++;
        if (profileListener != null) {
            profileListener.remove();
            profileListener = null;
        }
    }

    private void applyWindowInsets(View root) {
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                var safeArea = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                view.setPadding(safeArea.left, safeArea.top, safeArea.right, safeArea.bottom);
            } else {
                view.setPadding(
                        insets.getSystemWindowInsetLeft(),
                        insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(),
                        insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
    }
}
