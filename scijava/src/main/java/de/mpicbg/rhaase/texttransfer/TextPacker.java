package de.mpicbg.rhaase.texttransfer;

import de.mpicbg.ulman.imgtransfer.ArrayPacker;
import de.mpicbg.ulman.imgtransfer.ProgressCallback;
import de.mpicbg.ulman.transfer.AbstractPacker;
import net.imagej.ImgPlus;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.basictypeaccess.array.ArrayDataAccess;
import net.imglib2.img.cell.CellImg;
import net.imglib2.img.planar.PlanarImg;
import net.imglib2.type.NativeType;
import org.zeromq.ZMQ;

import java.util.StringTokenizer;

/**
 * TextPacker
 * <p>
 * <p>
 * <p>
 * Author: @haesleinhuepf
 * 10 2018
 */
public class TextPacker extends AbstractPacker {
    public static void packAndSend(String text, ZMQ.Socket socket, int timeOut, ProgressCallback log) {
        //"buffer" for the first and human-readable payload:
        //protocol version
        String msg = new String("v1");

        //dimensionality data
        msg += " dimNumber " + text.length();

        //decipher the voxel type
        msg += " String";

        msg += " String ";

        //send header, metadata and voxel data afterwards
        if (log != null) log.info("sending header: "+msg);
        packAndSendHeader(msg, socket, timeOut);
        if (log != null) log.info("sending the text...");
        packAndSendHeader(text, socket, timeOut);


        //wait for confirmation from the receiver
        ArrayPacker.waitForFirstMessage(socket);
        msg = socket.recvStr();
        if (! msg.startsWith("done"))
            throw new RuntimeException("Protocol error, expected final confirmation from the receiver.");
        if (log != null) log.info("sending finished...");
    }
}
