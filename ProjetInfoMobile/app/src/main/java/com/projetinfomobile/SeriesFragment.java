package com.projetinfomobile;

import android.app.ActionBar;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.MultiAutoCompleteTextView;
import android.widget.TextView;

import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.firebase.client.ChildEventListener;
import com.firebase.client.DataSnapshot;
import com.firebase.client.FirebaseError;
import com.firebase.ui.FirebaseRecyclerAdapter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import Model.DatabaseInterface;
import Model.OMDBInterface;
import Model.Serie;

public class SeriesFragment extends Fragment {

    public static class WatchedSerieViewHolder extends RecyclerView.ViewHolder {
        TextView title;
        TextView description;
        ImageView posterView;
        Button removeSerieButton;
        Button recommendSerieButton;
        public WatchedSerieViewHolder(View itemView) {
            super(itemView);
            title = (TextView)itemView.findViewById(R.id.serie_name);
            description = (TextView)itemView.findViewById(R.id.serie_description);
            posterView = (ImageView)itemView.findViewById(R.id.serie_poster);
            removeSerieButton =(Button)itemView.findViewById(R.id.remove_serie_button);
            recommendSerieButton =(Button)itemView.findViewById(R.id.recommend_serie_button);
        }
    }

    Button searchSeriesButton;
    EditText searchSerieTitle;
    RecyclerView searchSerieResults;
    RecyclerView watchedSeriesListview;
    OMDBInterface omdbInterface;
    FirebaseRecyclerAdapter<String, WatchedSerieViewHolder> watchedSeriesAdapter;
    SerieSearchResultAdapter serieSearchResultAdapter;
    ArrayAdapter<String> autoCompleteFriendAdapter;
    List<String> autoCompleteFriendsSuggestions = new ArrayList<>();
    int currentSearchPage = 0;

    public SeriesFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        autoCompleteFriendAdapter = new ArrayAdapter<String>(getContext(),android.R.layout.simple_dropdown_item_1line, autoCompleteFriendsSuggestions);
        DatabaseInterface.Instance().GetFriendListNode().addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                autoCompleteFriendAdapter.add(dataSnapshot.getValue(String.class));
            }

            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String s) {

            }

            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) {
                autoCompleteFriendAdapter.remove(dataSnapshot.getValue(String.class));
            }

            @Override
            public void onChildMoved(DataSnapshot dataSnapshot, String s) {

            }

            @Override
            public void onCancelled(FirebaseError firebaseError) {

            }
        });

        serieSearchResultAdapter = new SerieSearchResultAdapter(getContext());
        omdbInterface = OMDBInterface.Start(getContext());

        View view = inflater.inflate(R.layout.fragment_series, container, false);
        searchSeriesButton = (Button)view.findViewById(R.id.search_serie_button);
        searchSeriesButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                serieSearchResultAdapter.clear();
                currentSearchPage = 1;
                omdbInterface.SearchSerie(searchSerieTitle.getText().toString().trim(), currentSearchPage, onSerieSearchResponse, onSerieSearchError);
            }
        });
        searchSerieTitle = (EditText)view.findViewById(R.id.search_serie_title);
        watchedSeriesListview = (RecyclerView)view.findViewById(R.id.series_listview);
        watchedSeriesListview.setHasFixedSize(true);
        watchedSeriesListview.setLayoutManager(new LinearLayoutManager(getContext()));
        searchSerieResults = (RecyclerView)view.findViewById(R.id.search_results_recyclerview);
        searchSerieResults.setHasFixedSize(true);
        searchSerieResults.setLayoutManager(new LinearLayoutManager(getContext()));
        searchSerieResults.setAdapter(serieSearchResultAdapter);


        watchedSeriesAdapter = new FirebaseRecyclerAdapter<String, WatchedSerieViewHolder>(String.class, R.layout.series_listview_item, WatchedSerieViewHolder.class,DatabaseInterface.Instance().GetSeriesListNode()) {
            @Override
            protected void populateViewHolder(final WatchedSerieViewHolder view, final String serieID, int position) {
                Log.i("Populate", serieID);
                view.removeSerieButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        DatabaseInterface.Instance().DeleteSerie(serieID);
                    }
                });
                view.recommendSerieButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        RecommendSerie(serieID);
                    }
                });
                omdbInterface.GetSerieInfo(serieID, new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                            Log.i("Response", serieID);

                            Serie serie = Serie.FromJSONObject(response);
                            view.title.setText(serie.getName());
                            view.description.setText(serie.getDescription());
                            if(!serie.getPhotoURL().equalsIgnoreCase("N/A")) {
                                omdbInterface.GetPoster(serie.getPhotoURL(), view.posterView);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }, new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        error.printStackTrace();
                    }
                });
            }
        };
        watchedSeriesListview.setAdapter(watchedSeriesAdapter);

        return view;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        watchedSeriesAdapter.cleanup();
    }

    Response.Listener<JSONObject> onSerieSearchResponse = new Response.Listener<JSONObject>() {
        @Override
        public void onResponse(JSONObject response) {
            try {
                JSONArray results = response.getJSONArray("Search");
                for(int i = 0; i<results.length(); ++i){
                    serieSearchResultAdapter.add(Serie.FromJSONObject(results.getJSONObject(i)));
                }
                int nbResults = response.getInt("totalResults");
                if(serieSearchResultAdapter.getItemCount() < nbResults){
                    omdbInterface.SearchSerie(searchSerieTitle.getText().toString(), ++currentSearchPage, onSerieSearchResponse, onSerieSearchError);
                }
                else{
                    searchSeriesButton.setEnabled(true);
                }

            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    };

    Response.ErrorListener onSerieSearchError = new Response.ErrorListener() {
        @Override
        public void onErrorResponse(VolleyError error) {
            error.printStackTrace();
        }
    };

    void RecommendSerie(final String serieID){
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Recommend");

        final MultiAutoCompleteTextView input = new MultiAutoCompleteTextView(getContext());
        input.setAdapter(autoCompleteFriendAdapter);
        input.setThreshold(2);
        input.setTokenizer(new MultiAutoCompleteTextView.CommaTokenizer());

        builder.setView(input);

        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String[] friends = input.getText().toString().split(", ");
                for(int i = 0; i < friends.length; ++i){
                    DatabaseInterface.Instance().SendSerieSuggestion(friends[i], serieID);
                }
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        AlertDialog dialog = builder.show();
    }
}
