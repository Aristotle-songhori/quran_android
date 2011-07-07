package com.quran.labs.androidquran;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.AsyncTask.Status;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

import com.google.gson.Gson;
import com.quran.labs.androidquran.common.DownloadItem;
import com.quran.labs.androidquran.common.InternetActivity;
import com.quran.labs.androidquran.common.TranslationItem;
import com.quran.labs.androidquran.common.TranslationsDBAdapter;
import com.quran.labs.androidquran.util.QuranSettings;
import com.quran.labs.androidquran.util.QuranUtils;
import com.quran.labs.androidquran.util.RestClient;

public class DownloadActivity extends InternetActivity {
	
	public static final String WEB_SERVICE_URL = "http://labs.quran.com/androidquran/translations.php";
	private String url;
	private DownloadItem [] downloadItems;
	private ListView listView;
	private ProgressDialog progressDialog;
	private LoadTranslationsTask currentTask;
	private TranslationsDBAdapter dba;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.download_list);
        listView = (ListView) findViewById(R.id.download_list);
        progressDialog = new ProgressDialog(this);
        progressDialog.setCancelable(true);
        progressDialog.setOnCancelListener(new OnCancelListener() {
			public void onCancel(DialogInterface dialog) {
				cancelDownload();
			}
		});
        url = savedInstanceState != null ? savedInstanceState.getString("url") : WEB_SERVICE_URL;
        dba = new TranslationsDBAdapter(getApplicationContext());
        connect();
	}
	
	@Override
	protected void onConnectionSuccess() {
		fetchTranslationsList();
	}
	
	private void fetchTranslationsList() {
		currentTask = new LoadTranslationsTask();
		currentTask.execute((Object []) null);
	}
	
	private void populateList() {
		if (downloadItems == null) return;
		// Set up column mappings
		String [] dataColumns = new String [] {"displayName", "is_downloaded"};
		int [] dataColumnsIds = new int [] {R.id.display_name, R.id.is_downloaded};
		
		// Now iterate on all records
		List<HashMap<String, String>> lst = new ArrayList<HashMap<String,String>>();
		for (int i = 0; i < downloadItems.length; i++) {
			HashMap<String, String> map = new HashMap<String, String>();
			map.put("displayName", downloadItems[i].getDisplayName());
			map.put("is_downloaded",downloadItems[i].isDownloaded() ? "Downloaded" : "");
			lst.add(map);
		}
		
		SimpleAdapter adapter = new SimpleAdapter(this, lst, R.layout.download_row, dataColumns, dataColumnsIds);
		listView.setAdapter(adapter);
		listView.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, final View view, final int position, long id) {
				final DownloadItem item = downloadItems[position];
				if (!item.isDownloaded()) {
					AlertDialog.Builder builder = new AlertDialog.Builder(DownloadActivity.this);
					builder.setMessage("Download Translation '" + item.getDisplayName() + "' ?")
					       .setCancelable(true)
					       .setPositiveButton("Download", new DialogInterface.OnClickListener() {
					           public void onClick(DialogInterface dialog, int id) {
					        	   downloadTranslation(downloadItems[position].getFileUrl(), downloadItems[position].getFileName());
					        	   dialog.dismiss();
					           }
					       });
					AlertDialog alert = builder.create();
					alert.show();
				} else {
					AlertDialog.Builder builder = new AlertDialog.Builder(DownloadActivity.this);
					builder.setMessage("'" + item.getDisplayName() + "' is already downloaded.")
					       .setCancelable(true)
					       .setPositiveButton("Remove", new DialogInterface.OnClickListener() {
					           public void onClick(DialogInterface dialog, int id) {
					        	   boolean removed = QuranUtils.removeTranslation(item.getFileName());
					        	   if (removed) {
					        		   TextView t = (TextView) view.findViewById(R.id.is_downloaded);
					        		   t.setText("");					        		   
					        	   }					        		   
					        	   dialog.dismiss();
					           }
					       })
					       .setNegativeButton("Set Active", new DialogInterface.OnClickListener() {
					           public void onClick(DialogInterface dialog, int id) {
					        	   QuranSettings.getInstance().setActiveTranslation(item.getFileName());
					        	   QuranSettings.save(prefs);
					        	   dialog.dismiss();
					           }
					       });
					AlertDialog alert = builder.create();
					alert.show();
				}
			}
		});
	}
	
	private void sendRequest() {
		JSONObject jsonObject;
		
		try {
			jsonObject = RestClient.connect(url, null, null);
	        JSONArray jsonArray = jsonObject.getJSONArray("data");
	        int nItems = jsonArray.length();
	        downloadItems = new DownloadItem[nItems];
	        Gson gson = new Gson();
	        for (int i = 0; i < nItems; i++) {
	        	JSONObject json = jsonArray.getJSONObject(i);
	        	// Just for now use TranslationItem class..
	        	downloadItems[i] = gson.fromJson(json.toString(), TranslationItem.class);
	            Log.i("QuranAndroid","Load Translations: <jsonobject>\n"+json.toString()+"\n</jsonobject>");
	        }	
		} catch (JSONException e) {
			// Show Error msg
			Log.d("JSON Exception", e.getMessage());
		} catch (NullPointerException e){
			// Show Error msg
			Log.d("JSON Exception", "Empty message");
		}
	}

	private class LoadTranslationsTask extends AsyncTask<Object [], Object, Object>  {
		
		public void onPreExecute() {
			super.onPreExecute();
			currentTask = this;
			progressDialog.show();
			progressDialog.setMessage("Loading Translations List, Please wait..");
		}
		
		public String doInBackground(Object[]... params) {
			sendRequest();
			if (downloadItems != null) {
				int nrecords = dba.deleteAllRecords();
				Log.i("Translations DB", "Deleted " + nrecords + " records");
				dba.save(downloadItems);
			}
    		return null;
    	}
    	    	
    	@Override
    	public void onPostExecute(Object result){
    		super.onPostExecute(result);
    		currentTask = null;
    		progressDialog.hide();
			populateList();
    	}
    }
	
	@Override
	protected void onFinishDownload() {
		super.onFinishDownload();
		fetchTranslationsList();
	}
	
	public void cancelDownload(){
		progressDialog.dismiss();
		currentTask.cancel(true);
	}
	
	@Override
	protected void onDestroy(){
		super.onDestroy();
		progressDialog.dismiss();
		if ((currentTask != null) && (currentTask.getStatus() == Status.RUNNING))
			currentTask.cancel(true);
	}
}
