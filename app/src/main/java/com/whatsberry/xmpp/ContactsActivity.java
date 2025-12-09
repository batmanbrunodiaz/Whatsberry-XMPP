package com.whatsberry.xmpp;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Contacts Activity
 * Shows list of WhatsApp contacts
 */
public class ContactsActivity extends Activity {
    private ListView lvContacts;
    private Button btnRefresh, btnLogout;
    private TextView tvEmpty;
    private EditText etSearch;
    private ProgressDialog progressDialog;

    private XMPPManager xmppManager;
    private WhatsAppManager whatsAppManager;
    private ContactsAdapter adapter;
    private List<XMPPManager.Contact> contacts;
    private List<XMPPManager.Contact> filteredContacts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contacts);

        xmppManager = XMPPManager.getInstance();
        whatsAppManager = WhatsAppManager.getInstance();

        initializeViews();
        setupListeners();
        loadContacts();
    }

    private void initializeViews() {
        lvContacts = (ListView) findViewById(R.id.lvContacts);
        btnRefresh = (Button) findViewById(R.id.btnRefresh);
        btnLogout = (Button) findViewById(R.id.btnLogout);
        tvEmpty = (TextView) findViewById(R.id.tvEmpty);
        etSearch = (EditText) findViewById(R.id.etSearch);

        contacts = new ArrayList<>();
        filteredContacts = new ArrayList<>();
        adapter = new ContactsAdapter();
        lvContacts.setAdapter(adapter);
        lvContacts.setEmptyView(tvEmpty);

        progressDialog = new ProgressDialog(this);
        progressDialog.setCancelable(false);
    }

    private void setupListeners() {
        btnRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadContacts();
            }
        });

        btnLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                logout();
            }
        });

        // Search functionality
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterContacts(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        lvContacts.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                XMPPManager.Contact contact = filteredContacts.get(position);
                openChat(contact);
            }
        });
    }

    private void loadContacts() {
        progressDialog.setMessage("Loading contacts...");
        progressDialog.show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<XMPPManager.Contact> loadedContacts = xmppManager.getContacts();

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressDialog.dismiss();
                        contacts.clear();
                        contacts.addAll(loadedContacts);
                        filterContacts(etSearch.getText().toString());

                        if (contacts.isEmpty()) {
                            Toast.makeText(ContactsActivity.this,
                                    "No contacts found. Add contacts on WhatsApp first.",
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        }).start();
    }

    private void filterContacts(String query) {
        filteredContacts.clear();

        if (query == null || query.trim().isEmpty()) {
            filteredContacts.addAll(contacts);
        } else {
            String lowerQuery = query.toLowerCase();
            for (XMPPManager.Contact contact : contacts) {
                if (contact.name.toLowerCase().contains(lowerQuery) ||
                    contact.jid.toLowerCase().contains(lowerQuery)) {
                    filteredContacts.add(contact);
                }
            }
        }

        adapter.notifyDataSetChanged();
    }

    private void logout() {
        progressDialog.setMessage("Logging out...");
        progressDialog.show();

        whatsAppManager.logoutWhatsApp(new WhatsAppManager.LogoutCallback() {
            @Override
            public void onLogoutSuccess() {
                progressDialog.dismiss();
                xmppManager.disconnect();
                Toast.makeText(ContactsActivity.this, "Logged out", Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onLogoutError(String error) {
                progressDialog.dismiss();
                Toast.makeText(ContactsActivity.this, "Logout error: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void openChat(XMPPManager.Contact contact) {
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra("contactJid", contact.jid);
        intent.putExtra("contactName", contact.name);
        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }

    // Custom adapter for contacts
    private class ContactsAdapter extends ArrayAdapter<XMPPManager.Contact> {
        ContactsAdapter() {
            super(ContactsActivity.this, android.R.layout.simple_list_item_2, filteredContacts);
        }

        @Override
        public int getCount() {
            return filteredContacts.size();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                view = getLayoutInflater().inflate(android.R.layout.simple_list_item_2, parent, false);
            }

            XMPPManager.Contact contact = filteredContacts.get(position);

            TextView text1 = (TextView) view.findViewById(android.R.id.text1);
            TextView text2 = (TextView) view.findViewById(android.R.id.text2);

            text1.setText(contact.name);

            String status = contact.isOnline ? "Online" : "Offline";
            if (contact.status != null && !contact.status.isEmpty()) {
                status += " - " + contact.status;
            }
            text2.setText(status + " (" + contact.jid + ")");

            return view;
        }
    }
}
