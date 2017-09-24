package com.example.android.dutchpay;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;
import android.content.DialogInterface;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.gson.JsonObject;

import org.json.JSONException;
import org.json.JSONObject;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private FirebaseAuth mFirebaseAuth;
    private FirebaseUser mFirebaseUser;
    private DatabaseReference mDatabaseRef;
    private DatabaseReference mDatabaseUserRef;
    private ProgressDialog mProgressDialog;

    private Button add_balance;
    private Button access_camera;
    private Button access_gallery;
    private TextView t;

    private static final int TAKE_PHOTO = 1;
    private static final int CHOOSE_GALLERY = 2;

    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mFirebaseAuth = FirebaseAuth.getInstance();
        mFirebaseUser = mFirebaseAuth.getCurrentUser();
        mDatabaseRef = FirebaseDatabase.getInstance().getReference().child("Users");
        mDatabaseUserRef = mDatabaseRef.child(mFirebaseUser.getUid());
        mProgressDialog = new ProgressDialog(this);

        add_balance = (Button)findViewById(R.id.add_balance);
        add_balance.setOnClickListener(this);
        access_camera = (Button)findViewById(R.id.access_camera);
        access_camera.setOnClickListener(this);
        access_gallery = (Button)findViewById(R.id.access_gallery);
        access_gallery.setOnClickListener(this);
        t = (TextView)findViewById(R.id.balance);

        ValueEventListener postListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                // Get Post object and use the values to update the UI
                User u = dataSnapshot.getValue(User.class);
                processRequest(u);

                DecimalFormat df = new DecimalFormat("#.00");
                df.setRoundingMode(RoundingMode.CEILING);
                t.setText(df.format(u.getBalance()));
            }
            @Override
            public void onCancelled(DatabaseError databaseError) {
                // Getting Post failed, log a message
                Log.w(TAG, "loadPost:onCancelled", databaseError.toException());
                // ...
            }
        };
        mDatabaseUserRef.addValueEventListener(postListener);


        // set the title as the user email
        if (mFirebaseUser != null) {
            setTitle(mFirebaseUser.getEmail());
        }
        else {
            startActivity(new Intent(this, LogInActivity.class));
        }

        // for added balance
        if (getIntent().hasExtra("add_balance")) {
            requestBalance(getIntent().getExtras().getDouble("add_balance"));
        }
    }

    @Override
    public void onClick(View v) {
        if (v == add_balance) {
            addBalance();
        }
        if (v == access_camera) {
            accessCamera();
        }
        if (v == access_gallery) {
            accessGallery();
        }
    }

    public void processRequest(@NonNull User u) {
        if (u.getChange() == 0 || u.getChangeBy() == "")
            return;

        double amount = u.getChange();
        String by = u.getChangeBy();

        double balance = u.getBalance();
        if (balance + amount >= 0) {
            u.setBalance(balance + amount);
            if(amount < 0) {
                singlePayment(by, 0 - amount);
            }
        }
        mDatabaseUserRef.child("change").setValue(0);
        mDatabaseUserRef.child("balance").setValue(u.getBalance());
    }

    public void requestBalance(double amount) {
        mDatabaseUserRef.child("change").setValue(amount);
        mDatabaseUserRef.child("changeBy").setValue(mFirebaseUser.getUid());
    }

    public void requestPayment(List friends, double original_amount) {
        int length = friends.size();
        double amount = original_amount / (double) length;
        DecimalFormat df = new DecimalFormat("#.00");
        df.setRoundingMode(RoundingMode.CEILING);
        amount = Double.parseDouble(df.format(amount));

        for(int i = 0; i < length; i++) {
            singleRequest(friends.get(i).toString(), amount);
        }
    }

    public void singleRequest(String friend, double amount) {
        JSONObject change = new JSONObject();
        try {
            change.put("amount", 0 - amount);
            change.put("by", mFirebaseUser.getUid());
        }
        catch (JSONException e) {}
        mDatabaseRef.child(friend).child("change").setValue(change);
    }

    public void singlePayment(String friend, double amount) {
        JSONObject change = new JSONObject();
        try {
            change.put("amount", 0 - amount);
            change.put("by", mFirebaseUser.getUid());
        }
        catch (JSONException e) {}
        mDatabaseRef.child(friend).child("change").setValue(change);
    }

    /*private void payTheRequest(final String friend, final double amount) {

        mDatabaseRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {

                String me = mFirebaseUser.getUid();
                String friendUid;
                Map<String, Object> childUpdates = new HashMap<>();

                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    User user = snapshot.getValue(User.class);
                    if (user.username.equals(friend)) {
                        friendUid = user.uid;
                        childUpdates.put("/" + friendUid + "/balance/", user.balance + amount);
                    }
                    if (user.uid.equals(me)) {
                        childUpdates.put("/" + me + "/balance/", user.balance - amount);

                        // check if it is possible to send the amount of money
                        if (user.balance - amount < 0) {

                            // dialog box
                            LayoutInflater mLayoutInflater = LayoutInflater.from(MainActivity.this);
                            View mPromptView = mLayoutInflater.inflate(R.layout.dialog, null);
                            AlertDialog.Builder alertDialogBox = new AlertDialog.Builder(MainActivity.this);
                            alertDialogBox.setView(mPromptView);

                            alertDialogBox.setCancelable(false)
                                    .setNegativeButton("Cancel",
                                            new DialogInterface.OnClickListener() {
                                                public void onClick(DialogInterface dialog, int id) {
                                                    dialog.cancel();
                                                }
                                            });
                            // create an alert dialog
                            AlertDialog alert = alertDialogBox.create();
                            alert.show();
                            mProgressDialog.dismiss();
                            return;
                        }
                    }
                }

                mProgressDialog.setMessage("Uploading...");
                mProgressDialog.show();

                mDatabaseRef.updateChildren(childUpdates);
                mProgressDialog.dismiss();
            }
            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.d("PAY_REQUEST", "pay request failed");
            }
        });

    }*/





    // custom log out method on the right top corner as a menu
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.log_out_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        if (menuItem.getItemId() == R.id.log_out) {
            mFirebaseAuth.signOut();

            // bring the user back to the log in page
            Intent logInPage = new Intent(this, LogInActivity.class);
            startActivity(logInPage);
        }
        return true;
    }
    // helper function to toast a message
    public void toastMessage(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
    }

    public void addBalance() {
        startActivity(new Intent(getApplicationContext(), BalanceRequestActivity.class));
    }
    public void accessCamera() {
        dispatchTakePictureIntent();
    }
    public void accessGallery() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, "Select Picture"), CHOOSE_GALLERY);
    }

    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(takePictureIntent, TAKE_PHOTO);
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == TAKE_PHOTO) {
            if(resultCode == RESULT_OK) {
                Bitmap imageBitmap = (Bitmap) data.getExtras().get("data");
                String fileName = createImageFromBitmap(imageBitmap);
                Intent intent = new Intent(getApplicationContext(), ConfirmActivity.class);
                startActivity(intent);
            }
        }
        else if(requestCode == CHOOSE_GALLERY && data != null && data.getData() != null) {
            if(resultCode == RESULT_OK) {
                Uri imageUri = data.getData();
                try {
                    Bitmap imageBitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageUri);
                    String fileName = createImageFromBitmap(imageBitmap);
                    Intent intent = new Intent(getApplicationContext(), ConfirmActivity.class);
                    startActivity(intent);
                } catch (IOException e) {
                }
            }
        }
    }

    public String createImageFromBitmap(Bitmap bitmap) {
        String fileName = "receiptImage";
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bytes);
            FileOutputStream fo = openFileOutput(fileName, Context.MODE_PRIVATE);
            fo.write(bytes.toByteArray());
            // remember close file output
            fo.close();
        } catch (Exception e) {
            e.printStackTrace();
            fileName = null;
        }
        return fileName;
    }
}