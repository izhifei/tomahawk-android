/* == This file is part of Tomahawk Player - <http://tomahawk-player.org> ===
 *
 *   Copyright 2013, Christopher Reichert <creichert07@gmail.com>
 *   Copyright 2013, Enno Gottschalk <mrmaffen@googlemail.com>
 *
 *   Tomahawk is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   Tomahawk is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with Tomahawk. If not, see <http://www.gnu.org/licenses/>.
 */
package org.tomahawk.tomahawk_android;

import com.actionbarsherlock.app.SherlockFragment;

import org.tomahawk.libtomahawk.Album;
import org.tomahawk.libtomahawk.Artist;
import org.tomahawk.libtomahawk.Collection;
import org.tomahawk.libtomahawk.CollectionLoader;
import org.tomahawk.libtomahawk.TomahawkBaseAdapter;
import org.tomahawk.libtomahawk.TomahawkGridAdapter;
import org.tomahawk.libtomahawk.TomahawkListAdapter;
import org.tomahawk.libtomahawk.TomahawkStickyListHeadersListView;
import org.tomahawk.libtomahawk.Track;
import org.tomahawk.libtomahawk.UserCollection;
import org.tomahawk.libtomahawk.audio.PlaybackActivity;
import org.tomahawk.libtomahawk.database.UserPlaylistsDataSource;
import org.tomahawk.libtomahawk.hatchet.InfoSystem;
import org.tomahawk.libtomahawk.playlist.CustomPlaylist;
import org.tomahawk.libtomahawk.resolver.PipeLine;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.ListAdapter;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

public abstract class TomahawkFragment extends SherlockFragment
        implements LoaderManager.LoaderCallbacks<Collection> {

    public static final String TOMAHAWK_ALBUM_ID = "tomahawk_album_id";

    public static final String TOMAHAWK_TRACK_ID = "tomahawk_track_id";

    public static final String TOMAHAWK_ARTIST_ID = "tomahawk_artist_id";

    public static final String TOMAHAWK_PLAYLIST_ID = "tomahawk_playlist_id";

    public static final String TOMAHAWK_LIST_SCROLL_POSITION = "tomahawk_list_scroll_position";

    public static final String TOMAHAWK_TAB_ID = "tomahawk_tab_id";

    public static final String TOMAHAWK_QUERY_IDS = "tomahawk_query_ids";

    protected TomahawkApp mTomahawkApp;

    private TomahawkFragmentReceiver mTomahawkFragmentReceiver;

    protected ArrayList<String> mCurrentRequestIds = new ArrayList<String>();

    protected InfoSystem mInfoSystem;

    protected PipeLine mPipeline;

    protected ConcurrentHashMap<String, Track> mCorrespondingQueryIds
            = new ConcurrentHashMap<String, Track>();

    private UserPlaylistsDataSource mUserPlaylistsDataSource;

    protected TomahawkTabsActivity mActivity;

    TomahawkBaseAdapter mTomahawkBaseAdapter;

    TomahawkStickyListHeadersListView mList;

    GridView mGrid;

    protected int mCorrespondingStackId;

    private int mListScrollPosition = 0;

    protected Album mAlbum;

    protected Artist mArtist;

    protected CustomPlaylist mCustomPlaylist;

    private Drawable mProgressDrawable;

    final private Handler mHandler = new Handler();

    final private Runnable mRequestFocus = new Runnable() {
        public void run() {
            ((mTomahawkBaseAdapter instanceof TomahawkGridAdapter) ? mGrid : mList)
                    .focusableViewAvailable(
                            ((mTomahawkBaseAdapter instanceof TomahawkGridAdapter) ? mGrid
                                    : mList));
        }
    };

    private Handler mAnimationHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_UPDATE_ANIMATION:
                    if (mPipeline.isResolving()) {
                        mProgressDrawable.setLevel(mProgressDrawable.getLevel() + 500);
                        mActivity.getSupportActionBar().setLogo(mProgressDrawable);
                        mAnimationHandler.removeMessages(MSG_UPDATE_ANIMATION);
                        mAnimationHandler.sendEmptyMessageDelayed(MSG_UPDATE_ANIMATION, 50);
                    } else {
                        stopLoadingAnimation();
                    }
                    break;
            }
            return true;
        }
    });

    private static final int MSG_UPDATE_ANIMATION = 0x20;

    /**
     * Handles incoming {@link Collection} updated broadcasts.
     */
    private class TomahawkFragmentReceiver extends BroadcastReceiver {

        /*
         * (non-Javadoc)
         * 
         * @see
         * android.content.BroadcastReceiver#onReceive(android.content.Context,
         * android.content.Intent)
         */
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Collection.COLLECTION_UPDATED)) {
                onCollectionUpdated();
            }
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see android.support.v4.app.Fragment#onCreate(android.os.Bundle)
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            if (getArguments().containsKey(TOMAHAWK_LIST_SCROLL_POSITION)
                    && getArguments().getInt(TOMAHAWK_LIST_SCROLL_POSITION) > 0) {
                mListScrollPosition = getArguments().getInt(TOMAHAWK_LIST_SCROLL_POSITION);
            }
            if (getArguments().containsKey(TOMAHAWK_TAB_ID)
                    && getArguments().getInt(TOMAHAWK_TAB_ID) > 0) {
                mCorrespondingStackId = getArguments().getInt(TOMAHAWK_TAB_ID);
            }
            if (getArguments().containsKey(TOMAHAWK_QUERY_IDS)
                    && getArguments().getIntegerArrayList(TOMAHAWK_QUERY_IDS) != null) {
                for (String string : getArguments().getStringArrayList(TOMAHAWK_QUERY_IDS)) {
                    mCorrespondingQueryIds.put(string, new Track());
                }
            }
        }
        mTomahawkApp = ((TomahawkApp) mActivity.getApplication());
        mInfoSystem = mTomahawkApp.getInfoSystem();
        mPipeline = mTomahawkApp.getPipeLine();
    }

    /*
     * (non-Javadoc)
     * @see android.support.v4.app.ListFragment#onCreateView(android.view.LayoutInflater, android.view.ViewGroup, android.os.Bundle)
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        return inflater.inflate(R.layout.tomahawkfragment_layout, null, false);
    }

    /*
     * (non-Javadoc)
     * @see android.support.v4.app.Fragment#onViewCreated(android.view.View, android.os.Bundle)
     */
    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ensureList();
        if (mGrid != null) {
            mGrid.setSelection(mListScrollPosition);
        } else if (mList != null) {
            mList.setSelection(mListScrollPosition);
        }
    }

    /*
     * (non-Javadoc)
     * @see android.support.v4.app.Fragment#onDestroyView()
     */
    @Override
    public void onDestroyView() {
        mHandler.removeCallbacks(mRequestFocus);
        mList = null;
        mGrid = null;
        super.onDestroyView();
    }

    /*
     * (non-Javadoc)
     *
     * @see android.support.v4.app.Fragment#onResume()
     */
    @Override
    public void onResume() {
        super.onResume();

        mProgressDrawable = getResources().getDrawable(R.drawable.progress_indeterminate_tomahawk);

        adaptColumnCount();

        getSherlockActivity().getSupportLoaderManager().destroyLoader(getId());
        getSherlockActivity().getSupportLoaderManager().initLoader(getId(), null, this);

        if (mTomahawkFragmentReceiver == null) {
            mTomahawkFragmentReceiver = new TomahawkFragmentReceiver();
            IntentFilter intentFilter = new IntentFilter(Collection.COLLECTION_UPDATED);
            getActivity().registerReceiver(mTomahawkFragmentReceiver, intentFilter);
        }
        if (mTomahawkBaseAdapter instanceof TomahawkGridAdapter) {
            getGridView().setSelection(mListScrollPosition);
        } else {
            getListView().setSelection(mListScrollPosition);
        }

        mUserPlaylistsDataSource = new UserPlaylistsDataSource(mActivity,
                mTomahawkApp.getPipeLine());
        mUserPlaylistsDataSource.open();
    }

    /*
     * (non-Javadoc)
     *
     * @see android.support.v4.app.Fragment#onPause()
     */
    @Override
    public void onPause() {
        super.onPause();

        if (mTomahawkFragmentReceiver != null) {
            getActivity().unregisterReceiver(mTomahawkFragmentReceiver);
            mTomahawkFragmentReceiver = null;
        }
        if (mUserPlaylistsDataSource != null) {
            mUserPlaylistsDataSource.close();
        }
    }

    /*
     * (non-Javadoc)
     * @see com.actionbarsherlock.app.SherlockListFragment#onAttach(android.app.Activity)
     */
    @Override
    public void onAttach(Activity activity) {
        mActivity = (TomahawkTabsActivity) activity;
        super.onAttach(activity);
    }

    /*
     * (non-Javadoc)
     * @see com.actionbarsherlock.app.SherlockListFragment#onDetach()
     */
    @Override
    public void onDetach() {
        super.onDetach();
    }

    /* 
     * (non-Javadoc)
     * @see android.support.v4.app.Fragment#onConfigurationChanged(android.content.res.Configuration)
     */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        adaptColumnCount();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
            ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        android.view.MenuInflater inflater = mActivity.getMenuInflater();
        inflater.inflate(R.menu.popup_menu, menu);
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        TomahawkBaseAdapter.TomahawkListItem tomahawkListItem;
        if (info.position >= 0) {
            tomahawkListItem = ((TomahawkBaseAdapter.TomahawkListItem) mTomahawkBaseAdapter
                    .getItem(info.position));
        } else {
            tomahawkListItem = ((TomahawkListAdapter) mTomahawkBaseAdapter)
                    .getContentHeaderTomahawkListItem();
        }
        if (!(tomahawkListItem instanceof CustomPlaylist || (tomahawkListItem instanceof Track
                && mCustomPlaylist != null))) {
            menu.findItem(R.id.popupmenu_delete_item).setVisible(false);
        }
        if (tomahawkListItem instanceof CustomPlaylist) {
            menu.findItem(R.id.popupmenu_addtoplaylist_item).setVisible(false);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        UserCollection userCollection = ((UserCollection) mTomahawkApp.getSourceList()
                .getCollectionFromId(UserCollection.Id));
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item
                .getMenuInfo();
        TomahawkBaseAdapter.TomahawkListItem tomahawkListItem;
        if (info.position >= 0) {
            tomahawkListItem = ((TomahawkBaseAdapter.TomahawkListItem) mTomahawkBaseAdapter
                    .getItem(info.position));
        } else {
            tomahawkListItem = ((TomahawkListAdapter) mTomahawkBaseAdapter)
                    .getContentHeaderTomahawkListItem();
        }
        Bundle bundle = new Bundle();
        ArrayList<Track> tracks = new ArrayList<Track>();
        switch (item.getItemId()) {
            case R.id.popupmenu_delete_item:
                if (tomahawkListItem instanceof CustomPlaylist) {
                    mUserPlaylistsDataSource
                            .deleteUserPlaylist(((CustomPlaylist) tomahawkListItem).getId());
                } else if (tomahawkListItem instanceof Track && mCustomPlaylist != null) {
                    mUserPlaylistsDataSource.deleteTrackInUserPlaylist(mCustomPlaylist.getId(),
                            ((Track) tomahawkListItem).getId());
                }
                userCollection.updateUserPlaylists();
                return true;
            case R.id.popupmenu_play_item:
                if (tomahawkListItem instanceof Track) {
                    CustomPlaylist playlist;
                    if (mAlbum != null) {
                        tracks = mAlbum.getTracks();
                        playlist = CustomPlaylist.fromTrackList("Last used playlist", tracks,
                                (Track) tomahawkListItem);
                        playlist.setCurrentTrackIndex(info.position);
                    } else if (mArtist != null) {
                        tracks = mArtist.getTracks();
                        playlist = CustomPlaylist.fromTrackList("Last used playlist", tracks,
                                (Track) tomahawkListItem);
                        playlist.setCurrentTrackIndex(info.position);
                    } else if (mCustomPlaylist != null) {
                        tracks = mCustomPlaylist.getTracks();
                        playlist = CustomPlaylist.fromTrackList("Last used playlist", tracks,
                                (Track) tomahawkListItem);
                        playlist.setCurrentTrackIndex(info.position);
                    } else {
                        tracks.add((Track) tomahawkListItem);
                        playlist = CustomPlaylist.fromTrackList("Last used playlist", tracks,
                                (Track) tomahawkListItem);
                        playlist.setCurrentTrackIndex(0);
                    }
                    userCollection.setCachedPlaylist(playlist);
                    bundle.putBoolean(UserCollection.USERCOLLECTION_PLAYLISTCACHED, true);
                    bundle.putLong(PlaybackActivity.PLAYLIST_TRACK_ID,
                            ((Track) tomahawkListItem).getId());
                } else if (tomahawkListItem instanceof CustomPlaylist) {
                    bundle.putLong(PlaybackActivity.PLAYLIST_PLAYLIST_ID,
                            ((CustomPlaylist) tomahawkListItem).getId());
                } else if (tomahawkListItem instanceof Album) {
                    bundle.putLong(PlaybackActivity.PLAYLIST_ALBUM_ID,
                            ((Album) tomahawkListItem).getId());
                    bundle.putLong(PlaybackActivity.PLAYLIST_TRACK_ID,
                            ((Album) tomahawkListItem).getTracks().get(0).getId());
                } else if (tomahawkListItem instanceof Artist) {
                    bundle.putLong(PlaybackActivity.PLAYLIST_ARTIST_ID,
                            ((Artist) tomahawkListItem).getId());
                }
                Intent playbackIntent = new Intent(getActivity(), PlaybackActivity.class);
                playbackIntent.putExtra(PlaybackActivity.PLAYLIST_EXTRA, bundle);
                startActivity(playbackIntent);
                return true;
            case R.id.popupmenu_playaftercurrenttrack_item:
                if (tomahawkListItem instanceof Track) {
                    tracks.add((Track) tomahawkListItem);
                } else if (tomahawkListItem instanceof CustomPlaylist) {
                    tracks = ((CustomPlaylist) tomahawkListItem).getTracks();
                } else if (tomahawkListItem instanceof Album) {
                    tracks = ((Album) tomahawkListItem).getTracks();
                } else if (tomahawkListItem instanceof Artist) {
                    tracks = ((Artist) tomahawkListItem).getTracks();
                }
                if (mActivity.getPlaybackService().getCurrentPlaylist() != null) {
                    mActivity.getPlaybackService().addTracksToCurrentPlaylist(
                            mActivity.getPlaybackService().getCurrentPlaylist()
                                    .getCurrentTrackIndex() + 1, tracks);
                } else {
                    mActivity.getPlaybackService().addTracksToCurrentPlaylist(tracks);
                }
                return true;
            case R.id.popupmenu_appendtoplaybacklist_item:
                if (tomahawkListItem instanceof Track) {
                    tracks.add((Track) tomahawkListItem);
                } else if (tomahawkListItem instanceof CustomPlaylist) {
                    tracks = ((CustomPlaylist) tomahawkListItem).getTracks();
                } else if (tomahawkListItem instanceof Album) {
                    tracks = ((Album) tomahawkListItem).getTracks();
                } else if (tomahawkListItem instanceof Artist) {
                    tracks = ((Artist) tomahawkListItem).getTracks();
                }
                mActivity.getPlaybackService().addTracksToCurrentPlaylist(tracks);
                return true;
            case R.id.popupmenu_addtoplaylist_item:
                if (tomahawkListItem instanceof Track) {
                    tracks.add((Track) tomahawkListItem);
                } else if (tomahawkListItem instanceof CustomPlaylist) {
                    tracks = ((CustomPlaylist) tomahawkListItem).getTracks();
                } else if (tomahawkListItem instanceof Album) {
                    tracks = ((Album) tomahawkListItem).getTracks();
                } else if (tomahawkListItem instanceof Artist) {
                    tracks = ((Artist) tomahawkListItem).getTracks();
                }
                new ChoosePlaylistDialog(userCollection, tracks)
                        .show(mActivity.getSupportFragmentManager(), "ChoosePlaylistDialog");
                userCollection.updateUserPlaylists();
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    /**
     * Adjust the column count so it fits to the current screen configuration
     */
    public void adaptColumnCount() {
        if (getGridView() != null) {
            if (getResources().getConfiguration().orientation
                    == Configuration.ORIENTATION_LANDSCAPE) {
                getGridView().setNumColumns(4);
            } else {
                getGridView().setNumColumns(2);
            }
        }
    }

    /**
     * Called when a Collection has been updated.
     */
    protected void onCollectionUpdated() {
        getSherlockActivity().getSupportLoaderManager().restartLoader(getId(), null, this);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * android.support.v4.app.LoaderManager.LoaderCallbacks#onCreateLoader(int,
     * android.os.Bundle)
     */
    @Override
    public Loader<Collection> onCreateLoader(int id, Bundle args) {
        return new CollectionLoader(getActivity(), getCurrentCollection());
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * android.support.v4.app.LoaderManager.LoaderCallbacks#onLoadFinished(android
     * .support.v4.content.Loader, java.lang.Object)
     */
    @Override
    public void onLoadFinished(Loader<Collection> loader, Collection coll) {
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * android.support.v4.app.LoaderManager.LoaderCallbacks#onLoaderReset(android
     * .support.v4.content.Loader)
     */
    @Override
    public void onLoaderReset(Loader<Collection> loader) {
    }

    /**
     * Get the activity's list view widget.
     */
    public TomahawkStickyListHeadersListView getListView() {
        ensureList();
        return mList;
    }

    /**
     * Get the activity's list view widget.
     */
    public GridView getGridView() {
        ensureList();
        return mGrid;
    }

    /**
     * Get the ListAdapter associated with this activity's ListView.
     */
    public ListAdapter getListAdapter() {
        return mTomahawkBaseAdapter;
    }

    private void ensureList() {
        if (((mTomahawkBaseAdapter instanceof TomahawkGridAdapter) ? mGrid : mList) != null) {
            return;
        }
        View root = getView();
        if (root == null) {
            throw new IllegalStateException("Content view not yet created");
        }
        if (root instanceof TomahawkStickyListHeadersListView) {
            mList = (TomahawkStickyListHeadersListView) root;
        } else if (root instanceof GridView) {
            mGrid = (GridView) root;
        } else {
            if (!(mTomahawkBaseAdapter instanceof TomahawkGridAdapter)) {
                View rawListView = root.findViewById(R.id.listview);
                if (!(rawListView instanceof TomahawkStickyListHeadersListView)) {
                    if (rawListView == null) {
                        throw new RuntimeException(
                                "Your content must have a TomahawkStickyListHeadersListView whose id attribute is "
                                        + "'R.id.listview'");
                    }
                    throw new RuntimeException("Content has view with id attribute 'R.id.listview' "
                            + "that is not a TomahawkStickyListHeadersListView class");
                }
                mList = (TomahawkStickyListHeadersListView) rawListView;
                registerForContextMenu(mList);
            } else {
                View rawListView = root.findViewById(R.id.gridview);
                if (!(rawListView instanceof GridView)) {
                    if (rawListView == null) {
                        throw new RuntimeException(
                                "Your content must have a GridView whose id attribute is "
                                        + "'R.id.gridview'");
                    }
                    throw new RuntimeException("Content has view with id attribute 'R.id.gridview' "
                            + "that is not a GridView class");
                }
                mGrid = (GridView) rawListView;
                registerForContextMenu(mGrid);
            }
        }
        if (mTomahawkBaseAdapter != null) {
            TomahawkBaseAdapter adapter = mTomahawkBaseAdapter;
            mTomahawkBaseAdapter = null;
            setListAdapter(adapter);
        }
        mHandler.post(mRequestFocus);
    }

    /**
     * Provide the cursor for the list view.
     */
    public void setListAdapter(TomahawkBaseAdapter adapter) {
        mTomahawkBaseAdapter = adapter;
        if (((mTomahawkBaseAdapter instanceof TomahawkGridAdapter) ? mGrid : mList) != null) {
            if (mTomahawkBaseAdapter instanceof TomahawkGridAdapter) {
                mGrid.setAdapter(adapter);
            } else {
                mList.setAdapter(adapter);
            }
        }
    }

    /**
     * @return the current Collection
     */
    public Collection getCurrentCollection() {
        if (mActivity != null) {
            return mActivity.getCollection();
        }
        return null;
    }

    /**
     * @return the current scrolling position of the list- or gridView
     */
    public int getListScrollPosition() {
        if (mTomahawkBaseAdapter instanceof TomahawkGridAdapter) {
            return getGridView().getFirstVisiblePosition();
        }
        return mListScrollPosition = getListView().getFirstVisiblePosition();
    }

    public void startLoadingAnimation() {
        mAnimationHandler.sendEmptyMessageDelayed(MSG_UPDATE_ANIMATION, 50);
    }

    public void stopLoadingAnimation() {
        mAnimationHandler.removeMessages(MSG_UPDATE_ANIMATION);
        mActivity.getSupportActionBar().setLogo(R.drawable.ic_action_slidemenu);
    }

    public ConcurrentHashMap<String, Track> getCorrespondingQueryIds() {
        return mCorrespondingQueryIds;
    }
}
