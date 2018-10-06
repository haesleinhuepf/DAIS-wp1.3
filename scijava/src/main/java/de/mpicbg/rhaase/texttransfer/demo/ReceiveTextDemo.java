package de.mpicbg.rhaase.texttransfer.demo;

import de.mpicbg.rhaase.texttransfer.TextTransfer;

import java.io.IOException;

/**
 * ReceiveTextDemo
 * <p>
 * <p>
 * <p>
 * Author: @haesleinhuepf
 * 10 2018
 */
public class ReceiveTextDemo {
    public static void main(String... args)  {
        for (int i = 0; i < 100; i++) {
            String result = null;
            System.out.println("Receiving...");
            try {
                result = TextTransfer.receiveText(4567, 10, new Logger());
            } catch (IOException e) {
                e.printStackTrace();
            }
            System.out.println("Received: " + result);
        }
    }
}
