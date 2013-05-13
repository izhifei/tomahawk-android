/* == This file is part of Tomahawk Player - <http://tomahawk-player.org> ===
 *
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

import org.tomahawk.libtomahawk.Track;
import org.tomahawk.libtomahawk.UserCollection;
import org.tomahawk.libtomahawk.audio.PlaylistDialog;
import org.tomahawk.libtomahawk.database.UserPlaylistsDataSource;
import org.tomahawk.libtomahawk.playlist.CustomPlaylist;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;

import java.util.ArrayList;

/**
 * Author Enno Gottschalk <mrmaffen@googlemail.com> Date: 24.02.13
 */

public class ChoosePlaylistDialog extends DialogFragment {

    UserCollection mUserCollection;

    ArrayList<Track> mTracks;

    int mCustomPlaylistCount;

    public ChoosePlaylistDialog(UserCollection userCollection, ArrayList<Track> tracks) {
        setRetainInstance(true);
        mUserCollection = userCollection;
        mTracks = tracks;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        mCustomPlaylistCount = mUserCollection.getCustomPlaylists().size();
        String[] playlistNames = new String[mCustomPlaylistCount + 1];
        for (int i = 0; i < mCustomPlaylistCount; i++) {
            playlistNames[i] = mUserCollection.getCustomPlaylists().get(i).getName();
        }
        playlistNames[mCustomPlaylistCount] = getResources()
                .getString(R.string.playbackactivity_create_playlist_dialog_title);
        builder.setTitle(R.string.playbackactivity_choose_playlist_dialog_title)
                .setItems(playlistNames, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which < mCustomPlaylistCount) {
                            UserPlaylistsDataSource userPlaylistsDataSource
                                    = new UserPlaylistsDataSource(getActivity(),
                                    ((TomahawkApp) getActivity().getApplication()).getPipeLine());
                            userPlaylistsDataSource.open();
                            userPlaylistsDataSource.addTracksToUserPlaylist(
                                    mUserCollection.getCustomPlaylists().get(which).getId(),
                                    mTracks);
                            userPlaylistsDataSource.close();
                            ((UserCollection) ((TomahawkApp) getActivity().getApplication())
                                    .getSourceList().getCollectionFromId(UserCollection.Id))
                                    .updateUserPlaylists();
                        } else {
                            new PlaylistDialog(CustomPlaylist.fromTrackList(mTracks))
                                    .show(getFragmentManager(), getString(
                                            R.string.playbackactivity_create_playlist_dialog_title));
                        }
                    }
                });
        return builder.create();
    }
}
