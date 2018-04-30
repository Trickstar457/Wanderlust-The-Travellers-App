package com.project.wanderlust.Activities;

import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.Toast;

import com.google.firebase.appindexing.FirebaseAppIndex;
import com.google.firebase.appindexing.Indexable;
import com.google.firebase.appindexing.builders.Indexables;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.project.wanderlust.Adapters.SelectedPicturesAdapter;
import com.project.wanderlust.Fragments.FragmentJourneysList;
import com.project.wanderlust.DataClasses.JourneyMini;
import com.project.wanderlust.R;
import com.project.wanderlust.Others.SharedFunctions;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;


public class ActivityCreateJourney extends ActionBarMenu
{
    public static final int CAMERA = 2;
    public static final int GALLERY = 3;

    public static String TITLE = "title";
    public static String DESCRIPTION = "description";
    public static String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";

    DatabaseReference mReference;

    private EditText title;
    private EditText description;

    final ArrayList<Bitmap> photos = new ArrayList<>();
    SelectedPicturesAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_journey);

        android.support.v7.app.ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);

        mReference = FirebaseDatabase.getInstance().getReference("Journeys").child(FirebaseAuth.getInstance().getCurrentUser().getPhoneNumber());

        title = findViewById(R.id.title);
        description = findViewById(R.id.description);

        adapter = new SelectedPicturesAdapter(this, photos);
        GridView gridView = findViewById(R.id.photoGrid);
        gridView.setAdapter(adapter);
    }


    //For Getting Pictures Using Gallery and Camera
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(resultCode == RESULT_OK && data != null) {
            if (requestCode == CAMERA)
            {
                photos.add((Bitmap) data.getExtras().get("data"));
                adapter.notifyDataSetChanged();
            }
            else if (requestCode == GALLERY)
            {
                if(photos.size() < 10)
                {
                    if (data.getClipData() != null)
                    {
                        ClipData mClipData = data.getClipData();
                        for (int i = 0; i < mClipData.getItemCount(); i++)
                        {
                            ClipData.Item item = mClipData.getItemAt(i);
                            Uri uri = item.getUri();
                            try {
                                Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), uri);

                                photos.add(bitmap);
                            }
                            catch (Exception e)
                            {

                            }
                        }
                        adapter.notifyDataSetChanged();
                    }
                }
                else Toast.makeText(this, "Cannot add more than 10 pictures", Toast.LENGTH_LONG).show();
            }
        }
    }




    public String getRealPathFromURI(Uri contentUri) {
        String res = null;
        String[] proj = { MediaStore.Images.Media.DATA };
        Cursor cursor = getContentResolver().query(contentUri, proj, null, null, null);
        if(cursor.moveToFirst()){
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            res = cursor.getString(column_index);
        }
        cursor.close();
        return res;
    }

    //-----------------------BUTTON LISTENERS--------------------------//
    public void createJourney(View view)
    {
        final String t = title.getText().toString();
        final String d = description.getText().toString();
        if(t.equals("")) {
            Toast.makeText(this, "Please give your journey a title", Toast.LENGTH_LONG).show();
            return;
        }

        final ProgressDialog dialog = ProgressDialog.show(this, "Please wait", "Creating Jounrey...", true);

        Date date = new Date();

        final String time = new SimpleDateFormat(DATE_FORMAT).format(date);

        new SaveImages(getApplicationContext(),time,photos).execute();

        //save title and description in online db
        final Map<String, String> map = new HashMap<>();
        map.put(TITLE, t);
        map.put(DESCRIPTION, d);
        mReference.child(time).setValue(map);

        //-------------------------------------------
        //Index Journey
        Indexable journeyToIndex = Indexables.noteDigitalDocumentBuilder()
                .setName(t)
                .setText(d)
                .setUrl("http://www.wanderlust.com/message/" + t)
                .build();

        FirebaseAppIndex.getInstance()
                .update(journeyToIndex);
        //-------------------------------------------

        Bitmap p = null;

        //select one image as journey pic
        if (photos.size() != 0)
        {
            Bitmap p1 = photos.get(0);

            ByteArrayOutputStream out = new ByteArrayOutputStream();

            p1.compress(Bitmap.CompressFormat.JPEG, 100, out);
            p = BitmapFactory.decodeStream(new ByteArrayInputStream(out.toByteArray()));
        }

        FragmentJourneysList.journeys.add(new JourneyMini(t, date, "Faisal Town", "Lahore",p));
        FragmentJourneysList.adapter.notifyDataSetChanged();

        Toast.makeText(this, "Journey Created Successfully!", Toast.LENGTH_LONG).show();

        onBackPressed();
    }

    public void Cancel(View view) { onBackPressed(); }

    public void getCameraPicture(View view) {
        Intent cameraIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
        startActivityForResult(cameraIntent, CAMERA);
    }

    public void getGalleryPictures(View view) {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent,"Select Pictures"), GALLERY);
    }


    //-----------ASYNC TASK TO SAVE IMAGES------------------//
    class SaveImages extends AsyncTask<Void, Integer, Void>
    {
        private Context context;
        private String time;
        private ArrayList<Bitmap> photos;

        public SaveImages(Context context, String time, ArrayList<Bitmap> photos)
        {
            this.context = context;
            this.time = time;
            this.photos = photos;
        }

        @Override
        protected Void doInBackground(Void... voids)
        {

            //Save Images Locally
            ContextWrapper wrapper = new ContextWrapper(getApplicationContext());
            File file = wrapper.getDir(time, Context.MODE_PRIVATE);

            for(int i = 0; i < photos.size(); i++)
            {
                File file1 = new File(file, i + ".jpg");
                try
                {
                    OutputStream stream = new FileOutputStream(file1);
                    photos.get(i).compress(Bitmap.CompressFormat.JPEG,100,stream);
                    stream.flush();
                    stream.close();
                } catch (Exception e) {}
            }

            return null;
        }
    }

}
