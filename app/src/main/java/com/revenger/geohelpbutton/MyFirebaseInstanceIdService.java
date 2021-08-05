package com.revenger.geohelpbutton;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;
import com.google.firebase.messaging.FirebaseMessagingService;

public class MyFirebaseInstanceIdService extends FirebaseMessagingService {

    private DatabaseReference UsersRef;
    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;

    @Override
    public void onNewToken(String s) {
        super.onNewToken(s);
        FirebaseInstanceId.getInstance().getInstanceId().addOnCompleteListener(new OnCompleteListener<InstanceIdResult>() {
            @Override
            public void onComplete(@NonNull Task<InstanceIdResult> task) {
                // проверка нашелся ли токен
                if (!task.isSuccessful()) {
                    return;
                }

                // инициализация
                mAuth = FirebaseAuth.getInstance();
                currentUser = mAuth.getCurrentUser();
                UsersRef = FirebaseDatabase.getInstance().getReference().child("Users");

                // определение токена
                String deviceToken = task.getResult().getToken();

                // запись нового токена
                if (currentUser != null) {
                    UsersRef.child(currentUser.getUid()).child("device_token").setValue(deviceToken);
                }
            }
        });
    }
}
