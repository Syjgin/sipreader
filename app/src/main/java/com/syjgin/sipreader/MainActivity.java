package com.syjgin.sipreader;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
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
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.jeremyfeinstein.slidingmenu.lib.SlidingMenu;
import com.nostra13.universalimageloader.cache.memory.impl.LruMemoryCache;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;


public class MainActivity extends AppCompatActivity {

    private static final String MAIN_URL = "http://sputnikipogrom.com/";

    private static final String PAGE_URL = "page/";
    private final float FADE_DEGREE = 0.35f;

    private final String IMAGES_LINKS = "imagesLinks";
    private final String URL_LINKS = "urlLinks";

    private final String MENU_LINKS = "menuLinks";
    private final String MENU_HEADERS = "menuHeaders";

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
    private SharedPreferences mPrefs;
    private ProgressBar mProgressbar;
    private boolean isMainMenuCreated;
    private int additionalHeight;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
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

            mProgressbar = (ProgressBar) findViewById(R.id.progressBar);

            mPrefs = getPreferences(MODE_PRIVATE);

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
            mToolbar.measure(0,0);
            additionalHeight = mToolbar.getMeasuredHeight();
            isMainMenuCreated =false;

            mSlider = new SlidingMenu(this);
            mSlider.setMode(SlidingMenu.LEFT);
            mSlider.setTouchModeAbove(SlidingMenu.TOUCHMODE_FULLSCREEN);
            mSlider.setFadeDegree(FADE_DEGREE);
            mSlider.attachToActivity(this, SlidingMenu.SLIDING_WINDOW);
            mSlider.setMenu(R.layout.sidemenu);
            mSlider.setBehindWidthRes(R.dimen.slidingmenu_behind_width);

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
                mProgressbar.setVisibility(View.INVISIBLE);
                Gson gson = new Gson();

                String imagesLinksJson = mPrefs.getString(IMAGES_LINKS, "");
                ArrayList imagesLinks = gson.fromJson(imagesLinksJson, ArrayList.class);

                String linksJson = mPrefs.getString(URL_LINKS, "");
                ArrayList urlLinks = gson.fromJson(linksJson, ArrayList.class);

                String menuLinksJson = mPrefs.getString(MENU_LINKS, "");
                ArrayList menuLinks = gson.fromJson(menuLinksJson, ArrayList.class);

                String menuHeadersJson = mPrefs.getString(MENU_HEADERS, "");
                ArrayList menuHeaders = gson.fromJson(menuHeadersJson, ArrayList.class);

                createMainMenu(menuHeaders, menuLinks);

                if(imagesLinks != null && urlLinks != null) {
                    loadImages(imagesLinks, urlLinks);
                } else {
                    Toast toast = Toast.makeText(getApplicationContext(), getString(R.string.no_connection), Toast.LENGTH_LONG);
                    toast.show();
                }

            } else {
                mCurrentTask = new PopulateImagesList();
                mCurrentTask.execute(mCurrentUrl);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void createMainMenu(List<String> headers, ArrayList<String> urls) {
        if(isMainMenuCreated)
            return;
        isMainMenuCreated = true;
        CompassSelectAdapter listValues = new CompassSelectAdapter(this, R.layout.sidemenu_item, headers, urls);

        ListView listView = ((ListView) findViewById(R.id.sidemenu));
        listView.setAdapter(listValues);
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

        private ArrayList<String> urls;

        public CompassSelectAdapter(Context context, int resource, List<String> objects, ArrayList<String> urls) {
            super(context, resource, objects);
            this.urls = urls;
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
            tv.setOnClickListener(new CompassSelectListener(urls.get(position)));
            return row;
        }
    }

    private class CompassSelectListener implements View.OnClickListener {

        private String currentUrl;

        public CompassSelectListener(String url) {
            currentUrl = url;
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
            loadByCompass(currentUrl);
        }
    }

    private class PopulateImagesList extends AsyncTask <String, Void, Void>
    {
        private ArrayList<String> imageLinks;
        private ArrayList<String> links;
        private ArrayList<String> menuHeaders;
        private ArrayList<String> menuLinks;
        private String url;

        @Override
        protected Void doInBackground(String... params) {
            url = params[0];
            try
            {
                Connection con = Jsoup.connect(url);
                Document doc = con.get();
                Elements imageElements = doc.select(".item-thumbnail img");
                Elements menuElements = doc.select(".menu-item a");
                Elements linksObjects = doc.select(".item-title > a");

                imageLinks = new ArrayList<>();
                links = new ArrayList<>();
                menuHeaders = new ArrayList<>();
                menuHeaders.add(0, "Главная");
                menuLinks = new ArrayList<>();
                menuLinks.add(0, MAIN_URL);

                for (int i =0; i < imageElements.size(); i++) {

                    Element image = imageElements.get(i);
                    String imageId = image.attr("src");
                    imageLinks.add(imageId);
                }

                for(int i = 0; i < menuElements.size(); i++) {
                    Element menuItem = menuElements.get(i);
                    String header = menuItem.text();
                    String url = menuItem.attr("href");
                    menuHeaders.add(i + 1, header);
                    menuLinks.add(i + 1, url);
                }

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
                if(mIsFirstTimeRefresh) {
                    mProgressbar.setVisibility(View.VISIBLE);
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
            mProgressbar.setVisibility(View.INVISIBLE);
            loadImages(imageLinks, links);

            SharedPreferences.Editor prefsEditor = mPrefs.edit();
            Gson gson = new Gson();
            String imagesLinksJson = gson.toJson(imageLinks);
            prefsEditor.putString(IMAGES_LINKS, imagesLinksJson);
            String linksJson = gson.toJson(links);
            prefsEditor.putString(URL_LINKS, linksJson);
            String headersJson = gson.toJson(menuHeaders);
            prefsEditor.putString(MENU_HEADERS, headersJson);
            String menulinksJson = gson.toJson(menuLinks);
            prefsEditor.putString(MENU_LINKS, menulinksJson);
            createMainMenu(menuHeaders, menuLinks);
            prefsEditor.commit();

            mIsLoadingInProgress = false;
        }
    }


    private void loadImages(ArrayList imageLinks, ArrayList links) {
        try {
            @SuppressWarnings("unchecked")
            ArrayList<String> convertedImg = (ArrayList<String>)imageLinks;
            @SuppressWarnings("unchecked")
            ArrayList<String> convertedLinks = (ArrayList<String>)links;
            if(convertedImg != null) {
                articlesAdapter.addData(convertedLinks, convertedImg);

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
                Space space = (Space)convertView.findViewById(R.id.space);
                space.setMinimumHeight(additionalHeight);
                convertView.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder)convertView.getTag();
            }
            DisplayImageOptions options = new DisplayImageOptions.Builder()
                    .cacheInMemory(true)
                    .cacheOnDisk(true)
                    .build();
            ImageLoader.getInstance().displayImage(imageLinks.get(position), viewHolder.imageView, options);
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
