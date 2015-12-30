package com.orbotix.calibration.api;

public interface CalibrationEventListener {
    /* Invoked when the Calibration has started. */
    void onCalibrationBegan();

    /* Invoked when the Calibraiton has been updated. */
    void onCalibrationChanged(final float angle);

    /* Invoked when the Calibration has stopped. */
    void onCalibrationEnded();
}
