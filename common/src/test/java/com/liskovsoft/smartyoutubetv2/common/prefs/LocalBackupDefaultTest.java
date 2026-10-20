package com.liskovsoft.smartyoutubetv2.common.prefs;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.liskovsoft.smartyoutubetv2.common.misc.LocalDriveBackupWorker;

import org.junit.Test;

public class LocalBackupDefaultTest {
    @Test
    public void automaticLocalBackupIsOffByDefault() {
        assertFalse(LocalDriveBackupWorker.isEnabled(GeneralData.DEFAULT_LOCAL_BACKUP_FREQ_DAYS));
    }

    @Test
    public void onlyAPositiveNumberOfDaysTurnsItOn() {
        assertFalse(LocalDriveBackupWorker.isEnabled(-1));
        assertFalse(LocalDriveBackupWorker.isEnabled(0));
        assertTrue(LocalDriveBackupWorker.isEnabled(1));
        assertTrue(LocalDriveBackupWorker.isEnabled(30));
    }
}
