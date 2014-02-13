package org.mozilla.mozstumbler.cellscanner;

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import org.mozilla.mozstumbler.ScannerService;
import org.mozilla.mozstumbler.preferences.Prefs;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

public class CellScanner {
    public static final String CELL_SCANNER_EXTRA_SUBJECT = "CellScanner";
    public static final String CELL_SCANNER_ARG_CELLS = "org.mozilla.mozstumbler.cellscanner.CellScanner.cells";

    private static final String LOGTAG = CellScanner.class.getName();
    private static final long CELL_MIN_UPDATE_TIME = 1000; // milliseconds

    private final Context mContext;
    private CellScannerImpl mImpl;
    private Timer mCellScanTimer;
    private final Set<String> mCells = new HashSet<String>();
    private int mCurrentCellInfoCount;

    interface CellScannerImpl {
        public void start();

        public void stop();

        public List<CellInfo> getCellInfo();
    }

    public CellScanner(Context context) {
        mContext = context;
    }

    public void start() {
        if (mImpl != null) {
            return;
        }
        Prefs prefs = new Prefs(mContext);

        if (prefs.isMultipleSimEnabled()) {
            try {
                mImpl = new GeminiCellScanner(mContext);
            } catch (UnsupportedOperationException uoe) {
                Log.e(LOGTAG, "CellScannerGeminiImpl() probe failed", uoe);
                mImpl = null;
            }
        }

        if (mImpl == null) {
            try {
                mImpl = new DefaultCellScanner(mContext);
            } catch (UnsupportedOperationException uoe) {
                Log.e(LOGTAG, "Cell scanner probe failed", uoe);
                return;
            }
        }

        mImpl.start();

        mCellScanTimer = new Timer();

        mCellScanTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                Log.d(LOGTAG, "Cell Scanning Timer fired");
                final long curTime = System.currentTimeMillis();
                ArrayList<CellInfo> cells = new ArrayList<CellInfo>(mImpl.getCellInfo());
                mCurrentCellInfoCount = cells.size();
                if (cells.isEmpty()) {
                    return;
                }
                for (CellInfo cell: cells) mCells.add(cell.getCellIdentity());

                Intent intent = new Intent(ScannerService.MESSAGE_TOPIC);
                intent.putExtra(Intent.EXTRA_SUBJECT, CELL_SCANNER_EXTRA_SUBJECT);
                intent.putParcelableArrayListExtra(CELL_SCANNER_ARG_CELLS, cells);
                intent.putExtra("time", curTime);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent);
            }
        }, 0, CELL_MIN_UPDATE_TIME);
    }

    public void stop() {
        if (mCellScanTimer != null) {
            mCellScanTimer.cancel();
            mCellScanTimer = null;
        }
        if (mImpl != null) {
            mImpl.stop();
            mImpl = null;
        }
    }

    public int getCellInfoCount() {
        return mCells.size();
    }

    public int getCurrentCellInfoCount() {
        return mCurrentCellInfoCount;
    }
}
