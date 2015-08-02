package com.syjgin.sipreader;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.jeremyfeinstein.slidingmenu.lib.SlidingMenu;
import com.nostra13.universalimageloader.cache.memory.impl.LruMemoryCache;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;


public class MainActivity extends ActionBarActivity {

    private static final String MAIN_URL = "http://sputnikipogrom.com/";
    private static final String COMPASS_RUSSIA = "http://sputnikipogrom.com/go/russia/";
    private static final String COMPASS_NOVOROSSIA = "http://sputnikipogrom.com/go/russia/novorossiya/";
    private static final String COMPASS_MINOR = "http://sputnikipogrom.com/go/russia/ua/";
    private static final String COMPASS_WEST = "http://sputnikipogrom.com/go/russia/west/";
    private static final String COMPASS_NEAR = "http://sputnikipogrom.com/go/empire/";
    private static final String COMPASS_EUROPE = "http://sputnikipogrom.com/go/europe/";
    private static final String COMPASS_USA = "http://sputnikipogrom.com/go/usa/";
    private static final String COMPASS_ASIA = "http://sputnikipogrom.com/go/asia/";
    private static final String COMPASS_MIDDLEEAST = "http://sputnikipogrom.com/go/greatermiddleeast/";
    private static final String COMPASS_WORLD = "http://sputnikipogrom.com/go/world/";

    private static final String PAGE_URL = "page/";
    private final float FADE_DEGREE = 0.35f;
    private final String IMAGES_LINKS = "imagesLinks";
    private final String URL_LINKS = "urlLinks";

    private int mCurrentPage;
    private String mCurrentUrl;
    private String mCurrentBaseUrl;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private boolean mIsFirstTimeRefresh;
    private boolean mIsLoadingInProgress;
    private SlidingMenu mSlider;
    private ArticlesAdapter articlesAdapter;
    private PopulateImagesList mCurrentTask;
    private ListView mArticlesListView;
    private Toolbar mToolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            //TODO: change displayImageOptions
            ImageLoaderConfiguration config = new ImageLoaderConfiguration.Builder(this).memoryCache(new LruMemoryCache(2 * 1024 * 1024))
                    .memoryCacheSize(2 * 1024 * 1024).diskCacheSize(50 * 1024 * 1024)
                    .diskCacheFileCount(100).build();
            ImageLoader.getInstance().init(config);
            mCurrentPage = 1;
            setContentView(R.layout.activity_main);
            mCurrentUrl = MAIN_URL;
            mCurrentBaseUrl = MAIN_URL;
            mSwipeRefreshLayout = (SwipeRefreshLayout)findViewById(R.id.swipe_refresh);
            mSwipeRefreshLayout.setOnRefreshListener(new RefreshListener());
            mIsFirstTimeRefresh = true;
            mIsLoadingInProgress = false;

            mToolbar = (Toolbar) findViewById(R.id.toolbar); // Attaching the layout to the toolbar object
            setSupportActionBar(mToolbar);
            mToolbar.setNavigationIcon(R.drawable.ic_menu_black_24dp);
            mToolbar.setNavigationOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!mSlider.isMenuShowing()) {
                        mSlider.showMenu(true);
                    }
                }
            });

            mSlider = new SlidingMenu(this);
            mSlider.setMode(SlidingMenu.LEFT);
            mSlider.setTouchModeAbove(SlidingMenu.TOUCHMODE_FULLSCREEN);
            mSlider.setFadeDegree(FADE_DEGREE);
            mSlider.attachToActivity(this, SlidingMenu.SLIDING_WINDOW);
            mSlider.setMenu(R.layout.sidemenu);
            mSlider.setBehindWidthRes(R.dimen.slidingmenu_behind_width);
            String values[] = getResources().getStringArray(R.array.compass_array);
            ArrayList<String> list = new ArrayList<>(values.length);
            for(int i = 0; i < values.length; i++) {
                list.add(i, values[i]);
            }
            CompassSelectAdapter listValues = new CompassSelectAdapter(this, R.layout.sidemenu_item, list);

            ListView listView = ((ListView) findViewById(R.id.sidemenu));
            listView.setAdapter(listValues);

            articlesAdapter = new ArticlesAdapter(getApplicationContext(), R.layout.articles_list_item, new String[0]);
            mArticlesListView = (ListView)findViewById(R.id.articlesList);
            mArticlesListView.setAdapter(articlesAdapter);
            mArticlesListView.setOnScrollListener(new EndlessScrollListener());
            startRefreshing();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startRefreshing() {
        if(mIsLoadingInProgress)
            return;
        try {
            ConnectivityManager cm =
                    (ConnectivityManager)getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            boolean isConnected = activeNetwork != null &&
                    activeNetwork.isConnectedOrConnecting();
            if(!isConnected) {
                /*boolean imagesLinksExistsInCache = false;
                try {
                    imagesLinksExistsInCache = Reservoir.contains(IMAGES_LINKS);
                } catch (Exception e) {};
                boolean linksExistsInCache = false;
                try {
                    linksExistsInCache = Reservoir.contains(URL_LINKS);
                } catch (Exception e) {};
                if(imagesLinksExistsInCache && linksExistsInCache) {
                    mSwipeRefreshLayout.setRefreshing(true);
                    ArrayList<String> imagesList = null;
                    ArrayList<String> urlLinks = null;
                    try {
                        imagesList = Reservoir.get(IMAGES_LINKS,ArrayList.class);
                    } catch (Exception e) {
                        Log.d("TAG", "fail");
                    }
                    try {
                        urlLinks = Reservoir.get(URL_LINKS,ArrayList.class);
                    } catch (Exception e) {
                        Log.d("TAG", "fail");
                    }
                    loadImages(imagesList, urlLinks);
                } else {
                    Toast toast = Toast.makeText(getApplicationContext(), getString(R.string.no_connection), Toast.LENGTH_LONG);
                    toast.show();
                }*/
            } else {
                mCurrentTask = new PopulateImagesList();
                mCurrentTask.execute(mCurrentUrl);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void ShowNextPage(View view) {

        mCurrentPage++;
        mCurrentUrl = mCurrentBaseUrl + PAGE_URL + mCurrentPage + "/";
        startRefreshing();
    }

    private class RefreshListener implements SwipeRefreshLayout.OnRefreshListener {

        @Override
        public void onRefresh() {
            startRefreshing();
        }
    }

    private class CompassSelectAdapter extends ArrayAdapter<String> {

        public CompassSelectAdapter(Context context, int resource, List<String> objects) {
            super(context, resource, objects);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row;

            if (convertView == null) {
                LayoutInflater inflater = getLayoutInflater();
                row = inflater.inflate(R.layout.sidemenu_item, parent, false);
            } else {
                row = convertView;
            }
            TextView tv = (TextView) row.findViewById(R.id.text);

            tv.setText(getItem(position));
            tv.setOnClickListener(new CompassSelectListener(getItem(position)));
            return row;
        }
    }

    private class CompassSelectListener implements View.OnClickListener {

        private String currentId;

        public CompassSelectListener(String id) {
            currentId = id;
        }

        private void loadByCompass(String url) {
            if(mIsLoadingInProgress) {
                mCurrentTask.cancel(true);
                mIsLoadingInProgress = false;
            }
            mCurrentBaseUrl = url;
            mCurrentUrl = url;
            mCurrentPage = 1;
            mSlider.showContent(true);
            articlesAdapter =  new ArticlesAdapter(getApplicationContext(), R.layout.articles_list_item, new String[0]);
            mArticlesListView.setAdapter(articlesAdapter);
            startRefreshing();
        }

        @Override
        public void onClick(View view) {
            if(this.currentId.equals(getString(R.string.russia))) {
                loadByCompass(COMPASS_RUSSIA);
            }
            if(this.currentId.equals(getString(R.string.novorossia))) {
                loadByCompass(COMPASS_NOVOROSSIA);
            }
            if(this.currentId.equals(getString(R.string.minorrussia))) {
                loadByCompass(COMPASS_MINOR);
            }
            if(this.currentId.equals(getString(R.string.westrussia))) {
                loadByCompass(COMPASS_WEST);
            }
            if(this.currentId.equals(getString(R.string.nearabroad))) {
                loadByCompass(COMPASS_NEAR);
            }
            if(this.currentId.equals(getString(R.string.europe))) {
                loadByCompass(COMPASS_EUROPE);
            }
            if(this.currentId.equals(getString(R.string.usa))) {
                loadByCompass(COMPASS_USA);
            }
            if(this.currentId.equals(getString(R.string.asia))) {
                loadByCompass(COMPASS_ASIA);
            }
            if(this.currentId.equals(getString(R.string.greatermiddleeast))) {
                loadByCompass(COMPASS_MIDDLEEAST);
            }
            if(this.currentId.equals(getString(R.string.world))) {
                loadByCompass(COMPASS_WORLD);
            }
        }
    }

    private class PopulateImagesList extends AsyncTask <String, Void, Void>
    {
        private ProgressBar progressBar;
        private ArrayList<String> imageLinks;
        private ArrayList<String> links;
        private String url;

        @Override
        protected Void doInBackground(String... params) {
            url = params[0];
            try
            {
                Connection con = Jsoup.connect(url);
                Document doc = con.get();
                Elements imageElements = doc.select(".item-thumbnail img");
                imageLinks = new ArrayList<>();
                links = new ArrayList<>();
                for (int i =0; i < imageElements.size(); i++) {

                    Element image = imageElements.get(i);
                    String imageId = image.attr("src");
                    imageLinks.add(imageId);
                }
                Elements linksObjects = doc.select(".item-title > a");
                for(Element currentLink : linksObjects) {
                    String link = currentLink.attr("href");
                    links.add(link);
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            mIsLoadingInProgress = true;
            try {
                progressBar = (ProgressBar) findViewById(R.id.progressBar);
                if(mIsFirstTimeRefresh) {
                    progressBar.setVisibility(View.VISIBLE);
                    mIsFirstTimeRefresh = false;
                }
                mSwipeRefreshLayout.setRefreshing(true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);
            progressBar.setVisibility(View.INVISIBLE);
            loadImages(imageLinks, links);
            /*Reservoir.putAsync(IMAGES_LINKS, imageLinks);
            Reservoir.putAsync(URL_LINKS, links);*/
            mIsLoadingInProgress = false;
        }
    }

    private void loadImages(ArrayList<String> imageLinks, ArrayList<String> links) {
        try {
            if(imageLinks != null) {
                articlesAdapter.addData(links, imageLinks);

            } else {

                Toast toast = Toast.makeText(getApplicationContext(), getString(R.string.connection_error), Toast.LENGTH_LONG);
                toast.show();
            }
            mSwipeRefreshLayout.setRefreshing(false);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static class ViewHolder {
        ImageView imageView;
    }

    private class ArticlesAdapter extends ArrayAdapter<String> {

        private ArrayList<String>imageLinks;
        private ArrayList<String>links;
        public ArticlesAdapter(Context context, int resource, String[] objects) {
            super(context, resource, objects);
            imageLinks = new ArrayList<>();
            links = new ArrayList<>();
        }

        public void addData(ArrayList<String>newLinks, ArrayList<String> newImageLinks) {
            for(String currentLink : newLinks) {
                if(!links.contains(currentLink))
                    links.add(currentLink);
            }
            for(String currentLink : newImageLinks) {
                if(!imageLinks.contains(currentLink))
                    imageLinks.add(currentLink);
            }
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return links.size();
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            ViewHolder viewHolder;
            if(convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.articles_list_item, parent, false);
                viewHolder = new ViewHolder();
                viewHolder.imageView = (ImageView)convertView.findViewById(R.id.image);
                convertView.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder)convertView.getTag();
            }
            ImageLoader.getInstance().displayImage(imageLinks.get(position), viewHolder.imageView);
            viewHolder.imageView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(getContext(), BrowserActivity.class);
                    intent.setData(Uri.parse(links.get(position)));
                    startActivity(intent);
                }
            });
            return convertView;
        }
    }

    public class EndlessScrollListener implements AbsListView.OnScrollListener {

        private int visibleThreshold = 5;
        private int previousTotal = 0;
        private boolean loading = true;

        public EndlessScrollListener() {
        }

        @Override
        public void onScroll(AbsListView view, int firstVisibleItem,
                             int visibleItemCount, int totalItemCount) {
            if (loading) {
                if (totalItemCount > previousTotal) {
                    loading = false;
                    previousTotal = totalItemCount;
                }
            }
            if (!loading && (totalItemCount - visibleItemCount) <= (firstVisibleItem + visibleThreshold)) {
                ShowNextPage(null);
                loading = true;
            }
        }

        @Override
        public void onScrollStateChanged(AbsListView view, int scrollState) {
        }
    }
}
