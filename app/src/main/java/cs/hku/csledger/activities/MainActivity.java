package cs.hku.csledger.activities;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.ShareActionProvider;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;
import androidx.drawerlayout.widget.DrawerLayout;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.Toast;

import com.google.android.material.navigation.NavigationView;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

import cs.hku.csledger.R;
import cs.hku.csledger.LedgerDatabaseHelper;
import cs.hku.csledger.adapter.LedgerAdapter;

public class MainActivity extends AppCompatActivity  implements ShareActionProvider.OnShareTargetSelectedListener {
    private LedgerDatabaseHelper dbHelper;
    private int lastIndex = 0;
    private LedgerAdapter adapter;
    private ShareActionProvider shareProvider;

    public void gotoAccountsView() {
        Intent intent = new Intent(this, AccountsOverview.class);
        startActivityForResult(intent, 20);
    }

    public void gotoAddView(View view) {
        Intent intent = new Intent(this, EditEntryActivity.class);
        startActivityForResult(intent, 1);
    }

    public void selectCheckbox(View view) {
        CheckBox box = (CheckBox) view;
        int size = 0;

        if (box.isChecked()) {
            size = adapter.getSelected().size();
            adapter.setSelected((Integer) box.getTag());
        } else {
            adapter.unsetSelected((Integer) box.getTag());
            size = adapter.getSelected().size();
        }

        if (size < 1) {
            invalidateOptionsMenu();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        this.adapter.notifyDataSetChanged();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        dbHelper = new LedgerDatabaseHelper(this);
        adapter = new LedgerAdapter(this, dbHelper);

        final ListView listView = (ListView) findViewById(R.id.ledger_entries);

        listView.setAdapter(adapter);

        final Context ctx = this;

        final DrawerLayout drawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);

        NavigationView navigationView = (NavigationView) findViewById(R.id.navigation);
        navigationView.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                item.setChecked(!item.isCheckable());
                drawerLayout.closeDrawers();

                int id = item.getItemId();

                //noinspection SimplifiableIfStatement
                switch (id) {
                    case R.id.action_settings:
                        Intent intent = new Intent(ctx, SettingsActivity.class);
                        startActivityForResult(intent, 1);
                        break;
                    case R.id.show_accounts:
                        gotoAccountsView();
                        break;
                    default:
                        return false;
                }

                return true;
            }
        });

        ActionBarDrawerToggle actionBarDrawerToggle = new ActionBarDrawerToggle(this,drawerLayout,toolbar,R.string.openDrawer, R.string.closeDrawer){

            @Override
            public void onDrawerClosed(View drawerView) {
                super.onDrawerClosed(drawerView);
            }

            @Override
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
            }
        };
        drawerLayout.addDrawerListener(actionBarDrawerToggle);
        actionBarDrawerToggle.syncState();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        int id = item.getItemId();

        switch (id) {
            case R.id.menu_item_share:
                exportAndSend();
                break;
            case R.id.toggle_all:
                adapter.setAll();
                invalidateOptionsMenu();
                break;
            case R.id.process_item:
                processItems();
                break;
            case R.id.menu_item_add:
                Intent intent = new Intent(this, EditEntryActivity.class);
                startActivityForResult(intent, 1);
                break;
            default:
                return super.onOptionsItemSelected(item);
        }

        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.ledger_overview_action_bar, menu);
        return true;
    }

    private void setShareIntent(Intent intent) {
        if (shareProvider != null) {
            shareProvider.setShareIntent(intent);
        }
    }

    private Uri generateExport() {
        StringBuilder builder = new StringBuilder();

        Map<Integer, Boolean> map = adapter.getSelected();
        for (int pos : map.keySet()) {
            if (map.get(pos)) {
                builder.append(adapter.getItem(pos).getExportString());
            }
        }

        builder.append("\n");

        DateFormat df = new SimpleDateFormat("yyyy_MM_dd__HH_mm_ss");

        String filename = "export_" + df.format(new Date()) + ".csledger";
        FileOutputStream outputStream;

        File file = new File(getFilesDir(), "exports");
        if (file == null) {
            Toast toast = Toast.makeText(this, "No such directory.", Toast.LENGTH_LONG);
            toast.show();
            return null;
        }

        if (!file.exists()) {
            file.mkdir();
        }

        file = new File(file, filename);

        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                Toast toast = Toast.makeText(this, e.getLocalizedMessage(), Toast.LENGTH_LONG);
                toast.show();
                return null;
            }
        }

        if (!file.canWrite()) {
            Toast toast = Toast.makeText(this, "Cannot write file " + filename, Toast.LENGTH_LONG);
            toast.show();
            return null;
        }

        try {
            outputStream = new FileOutputStream(file);
        } catch (FileNotFoundException e) {
            Toast toast = Toast.makeText(this, e.getLocalizedMessage(), Toast.LENGTH_LONG);
            toast.show();
            return null;
        }

        try {
            outputStream.write(builder.toString().getBytes());
            outputStream.close();
        } catch (IOException e) {
            Toast toast = Toast.makeText(this, e.getLocalizedMessage(), Toast.LENGTH_LONG);
            toast.show();
            return null;
        }

        return FileProvider.getUriForFile(MainActivity.this, "cs.hku.csledger.fileprovider", file);
    }

    private void exportAndSend() {
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        Uri uri = generateExport();

        if (uri == null) {
            return;
        }
        sendIntent.putExtra(Intent.EXTRA_STREAM, uri);
        sendIntent.setType(getContentResolver().getType(uri));
        sendIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(sendIntent, getResources().getText(R.string.send_to)));
        invalidateOptionsMenu();
    }

    public void processItems() {
        Map<Integer, Boolean> map = adapter.getSelected();
        for (int key : map.keySet()) {
            if (map.get(key)) {
                dbHelper.setProcessed(adapter.getItem(key));
            }
        }

        adapter.getSelected().clear();
        adapter.notifyDataSetChanged();

        Toast toast = Toast.makeText(this, getResources().getString(R.string.item_processed), Toast.LENGTH_LONG);
        toast.show();
        invalidateOptionsMenu();
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        Boolean selection = adapter.hasSelection();
        menu.findItem(R.id.process_item).setEnabled(selection);
        menu.findItem(R.id.menu_item_share).setEnabled(selection);
        return true;
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public boolean onShareTargetSelected(ShareActionProvider source, Intent intent) {
        exportAndSend();
        return true;
    }
}