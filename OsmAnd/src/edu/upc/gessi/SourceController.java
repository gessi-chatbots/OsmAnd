package edu.upc.gessi;

import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;

import net.osmand.GPXUtilities;
import net.osmand.plus.measurementtool.MeasurementToolFragment;

public class SourceController {

    public static void planRoute(MeasurementToolFragment measurementToolFragment, GPXUtilities.GPXFile savedGpxFile) {

        //Register
        //BroadcastReceiver br = new MyBroadcastReceiver();
        //IntentFilter filter = new IntentFilter("edu.upc.gessi.broadcast.TEST_BROADCAST");
        //filter.addAction("edu.upc.gessi.broadcast.TEST_BROADCAST");
        //measurementToolFragment.getActivity().registerReceiver(br, filter);


        Intent intent = new Intent();
        intent.setAction("edu.upc.gessi.broadcast.OSMAND.PLAN_ROUTE");
        intent.putExtra("name", savedGpxFile.tracks.get(0).name);
        intent.putExtra("init_lat", savedGpxFile.tracks.get(0).segments.get(0).points.get(0).getLatitude());
        intent.putExtra("init_long", savedGpxFile.tracks.get(0).segments.get(0).points.get(0).getLongitude());
        measurementToolFragment.getActivity().sendBroadcast(intent);
    }

}
