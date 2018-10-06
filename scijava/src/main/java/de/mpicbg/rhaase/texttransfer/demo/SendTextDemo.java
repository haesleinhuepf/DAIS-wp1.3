package de.mpicbg.rhaase.texttransfer.demo;

import de.mpicbg.rhaase.texttransfer.TextTransfer;

import java.io.IOException;
import java.util.Random;

/**
 * SendTextDemo
 * <p>
 * <p>
 * <p>
 * Author: @haesleinhuepf
 * 10 2018
 */
public class SendTextDemo {
    public static void main(String... args) throws InterruptedException {

        Random random = new Random();
        for (int i = 0; i < 100; i ++) {
            try {
                System.out.println("Sending");
                TextTransfer.sendText("Sent text TP " +
                        i, "tcp://127.0.0.1:4567", 10, new Logger());
                System.out.println("Sent");
            } catch (IOException e) {
                e.printStackTrace();
            }
            Thread.sleep((long) (10000.0 * random.nextFloat()));
        }
    }
}
