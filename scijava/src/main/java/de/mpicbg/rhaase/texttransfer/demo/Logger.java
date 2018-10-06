package de.mpicbg.rhaase.texttransfer.demo;

import de.mpicbg.ulman.imgtransfer.ProgressCallback;

/**
 * Logger
 * <p>
 * <p>
 * <p>
 * Author: @haesleinhuepf
 * 10 2018
 */
public class Logger implements ProgressCallback {
    @Override
    public void info(String msg) {
        System.out.println("Log: " + msg);
    }

    @Override
    public void setProgress(float howFar) {

    }
}
