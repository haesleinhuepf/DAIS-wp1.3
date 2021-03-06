/*
 * To the extent possible under law, the ImageJ developers have waived
 * all copyright and related or neighboring rights to this tutorial code.
 *
 * See the CC0 1.0 Universal license for details:
 *     http://creativecommons.org/publicdomain/zero/1.0/
 */
package de.mpicbg.ulman.imgtransfer;

import de.mpicbg.ulman.transfer.AbstractPacker;
import net.imagej.Dataset;
import net.imagej.ImgPlus;
import net.imglib2.img.Img;
import net.imglib2.img.WrappedImg;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.basictypeaccess.array.ArrayDataAccess;
import net.imglib2.img.cell.CellImg;
import net.imglib2.img.cell.CellImgFactory;
import net.imglib2.img.planar.PlanarImg;
import net.imglib2.img.planar.PlanarImgFactory;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.integer.ByteType;
import net.imglib2.type.numeric.integer.ShortType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;

import java.util.Arrays;
import java.util.List;
import java.util.StringTokenizer;

import org.zeromq.ZMQ;

public class ImgPacker extends AbstractPacker
{
	// -------- transmission of the image, sockets --------
	///list of supported voxel types: so far only scalar images are supported
	@SuppressWarnings("rawtypes")
	static List<Class<? extends NativeType>> SUPPORTED_VOXEL_CLASSES =
			Arrays.asList(ByteType.class, UnsignedByteType.class, ShortType.class,
					UnsignedShortType.class, FloatType.class, DoubleType.class);

	@SuppressWarnings("unchecked")
	static <T extends NativeType<T>>
	void packAndSend(final ImgPlus<T> imgP, final ZMQ.Socket socket,
	                 final int timeOut, final ProgressCallback log)
	{
		Class<?> voxelClass = imgP.firstElement().getClass();
		if(!SUPPORTED_VOXEL_CLASSES.contains(voxelClass))
			throw new IllegalArgumentException("Unsupported voxel type, sorry.");

		//"buffer" for the first and human-readable payload:
		//protocol version
		String msg = new String("v1");

		//dimensionality data
		msg += " dimNumber " + imgP.numDimensions();
		for (int i=0; i < imgP.numDimensions(); ++i)
			msg += " " + imgP.dimension(i);

		//decipher the voxel type
		msg += " " + voxelClass.getSimpleName();

		//check we can handle the storage model of this image,
		//and try to send everything (first the human readable payload, then raw voxel data)
		Img<T> img = getUnderlyingImg(imgP);
		if (img instanceof ArrayImg)
		{
			msg += " ArrayImg ";

			//send header, metadata and voxel data afterwards
			if (log != null) log.info("sending header: "+msg);
			packAndSendHeader(msg, socket, timeOut);
			if (log != null) log.info("sending the image...");
			packAndSendPlusData(imgP, socket);
			packAndSendArrayImg((ArrayImg<T,? extends ArrayDataAccess<?>>)img, socket);
		}
		else
		if (img instanceof PlanarImg)
		{
			//possibly add additional configuration hints to 'msg'
			msg += " PlanarImg "; //+((PlanarImg<T,?>)img).numSlices()+" ";
			//NB: The number of planes is deterministically given by the image size/dimensions.
			//    Hence, it is not necessary to provide such hint... 

			//TODO: if cell image will also need not to add extra header hints,
			//      we can move the 4 lines before this 3-branches-if

			//send header, metadata and voxel data afterwards
			if (log != null) log.info("sending header: "+msg);
			packAndSendHeader(msg, socket, timeOut);
			if (log != null) log.info("sending the image...");
			packAndSendPlusData(imgP, socket);
			packAndSendPlanarImg((PlanarImg<T,? extends ArrayDataAccess<?>>)img, socket);
		}
		else
		if (img instanceof CellImg)
		{
			//possibly add additional configuration hints to 'msg'
			msg += " CellImg ";
			throw new RuntimeException("Cannot send CellImg images yet.");

			//send header, metadata and voxel data afterwards
			//if (log != null) log.info("sending header: "+msg);
			//packAndSendHeader(msg, socket, timeOut);
			//if (log != null) log.info("sending the image...");
			//packAndSendPlusData(imgP, socket);
			//packAndSendCellImg((CellImg<T,?>)img, socket);
		}
		else
			throw new RuntimeException("Cannot determine the type of image, cannot send it.");

		//wait for confirmation from the receiver
		ArrayPacker.waitForFirstMessage(socket);
		msg = socket.recvStr();
		if (! msg.startsWith("done"))
			throw new RuntimeException("Protocol error, expected final confirmation from the receiver.");
		if (log != null) log.info("sending finished...");
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	static
	ImgPlus<?> receiveAndUnpack(final String header, final ZMQ.Socket socket,
	                            final ProgressCallback log)
	{
		if (log != null) log.info("received header: "+header);
		StringTokenizer headerST = new StringTokenizer(header, " ");
		if (! headerST.nextToken().startsWith("v1"))
			throw new RuntimeException("Unknown protocol, expecting protocol v1.");

		if (! headerST.nextToken().startsWith("dimNumber"))
			throw new RuntimeException("Incorrect protocol, expecting dimNumber.");
		final int n = Integer.valueOf(headerST.nextToken());

		//fill the dimensionality data
		final int[] dims = new int[n];
		for (int i=0; i < n; ++i)
			dims[i] = Integer.valueOf(headerST.nextToken());

		final String typeStr = new String(headerST.nextToken());
		final String backendStr = new String(headerST.nextToken());

		//envelope/header message is (mostly) parsed,
		//start creating the output image of the appropriate type
		Img<? extends NativeType<?>> img = createImg(dims, backendStr, createVoxelType(typeStr));

		if (img == null)
			throw new RuntimeException("Unsupported image backend type, sorry.");

		//if we got here, we assume that we have everything prepared to receive
		//the image, we therefore signal it to the sender
		socket.send("ready");
		if (log != null) log.info("receiving the image...");

		//the core Img is prepared, lets extend it with metadata and fill with voxel values afterwards
		//create the ImgPlus from it -- there is fortunately no deep coping
		ImgPlus<?> imgP = new ImgPlus<>(img);
		receiveAndUnpackPlusData((ImgPlus)imgP, socket);

		//populate with voxel data
		if (backendStr.startsWith("ArrayImg"))
		{
			receiveAndUnpackArrayImg((ArrayImg)img, socket);
		}
		else
		if (backendStr.startsWith("PlanarImg"))
		{
			//read possible additional configuration hints from 'header'
			//final int Slices = Integer.valueOf(headerST.nextToken());
			//and fine-tune the img
			receiveAndUnpackPlanarImg((PlanarImg)img, socket);
		}
		else
		if (backendStr.startsWith("CellImg"))
		{
			//read possible additional configuration hints from 'header'
			//and fine-tune the img
			throw new RuntimeException("Cannot receive CellImg images yet.");
			//receiveAndUnpackCellImg((CellImg)img, socket);
		}
		else
			throw new RuntimeException("Unsupported image backend type, sorry.");

		//send confirmation handshake after data has arrived
		socket.send("done");
		if (log != null) log.info("receiving finished...");

		return imgP;
	}


	// -------- support for the transmission of the image metadata --------
	///meta data Message Separator
	private static
	final String mdMsgSep = new String("__QWE__");

	private static <T>
	void packAndSendPlusData(final ImgPlus<T> imgP, final ZMQ.Socket socket)
	{
		//TODO: use JSON because metadata are of various types (including Strings)

		String msg = new String("metadata");
		msg += mdMsgSep+"imagename"+mdMsgSep+imgP.getName();
		msg += mdMsgSep+"endmetadata";
		socket.send(msg, ZMQ.SNDMORE);
	}

	private static <T>
	void receiveAndUnpackPlusData(final ImgPlus<T> imgP, final ZMQ.Socket socket)
	{
		//TODO: use JSON because metadata are of various types (including Strings)

		//read the single message
		ArrayPacker.waitForFirstMessage(socket);
		final String data = socket.recvStr();

		if (! data.startsWith("metadata"))
			throw new RuntimeException("Protocol error, expected metadata part from the receiver.");

		//split the input data to individual terms
		String[] terms = data.split(mdMsgSep);

		//should do more thorough tests....
		if (terms.length != 4)
			throw new RuntimeException("Protocol error, received likely corrupted metadata part.");

		//set filename
		imgP.setName(terms[2]);
	}


	// -------- support for the transmission of the payload/voxel data --------
	private static <T extends NativeType<T>>
	void packAndSendArrayImg(final ArrayImg<T,? extends ArrayDataAccess<?>> img, final ZMQ.Socket socket)
	{
		if (img.size() == 0)
			throw new RuntimeException("Refusing to send an empty image...");

		final Object data = img.update(null).getCurrentStorageArray();
		final ArrayPacker as = new ArrayPacker(data, socket, ArrayPacker.FROM_ARRAY_TO_SOCKET);
		as.transmitArray(data, false);
	}

	private static <T extends NativeType<T>>
	void receiveAndUnpackArrayImg(final ArrayImg<T,? extends ArrayDataAccess<?>> img, final ZMQ.Socket socket)
	{
		if (img.size() == 0)
			throw new RuntimeException("Refusing to receive an empty image...");

		final Object data = img.update(null).getCurrentStorageArray();
		final ArrayPacker ar = new ArrayPacker(data, socket, ArrayPacker.FROM_SOCKET_TO_ARRAY);
		ar.transmitArray(data, false);
	}

	private static <T extends NativeType<T>>
	void packAndSendPlanarImg(final PlanarImg<T,? extends ArrayDataAccess<?>> img, final ZMQ.Socket socket)
	{
		if (img.size() == 0)
			throw new RuntimeException("Refusing to send an empty image...");

		//TODO: remember the first array, transmitArray-it, and start for-cycle with slice=1
		final ArrayPacker as = new ArrayPacker(img.getPlane(0).getCurrentStorageArray(),
		                                           socket, ArrayPacker.FROM_ARRAY_TO_SOCKET);
		for (int slice = 0; slice < img.numSlices()-1; ++slice)
		{
			final Object data = img.getPlane(slice).getCurrentStorageArray();
			as.transmitArray(data, true);
		}
		{
			final Object data = img.getPlane(img.numSlices()-1).getCurrentStorageArray();
			as.transmitArray(data, false);
		}
	}

	private static <T extends NativeType<T>>
	void receiveAndUnpackPlanarImg(final PlanarImg<T,? extends ArrayDataAccess<?>> img, final ZMQ.Socket socket)
	{
		if (img.size() == 0)
			throw new RuntimeException("Refusing to receive an empty image...");

		//TODO: remember the first array, transmitArray-it, and start for-cycle with slice=1
		final ArrayPacker ar = new ArrayPacker(img.getPlane(0).getCurrentStorageArray(),
		                                           socket, ArrayPacker.FROM_SOCKET_TO_ARRAY);
		for (int slice = 0; slice < img.numSlices()-1; ++slice)
		{
			final Object data = img.getPlane(slice).getCurrentStorageArray();
			ar.transmitArray(data, false);
		}
		{
			final Object data = img.getPlane(img.numSlices()-1).getCurrentStorageArray();
			ar.transmitArray(data, false);
		}
	}


	// -------- the types war --------
	/*
	 * Keeps unwrapping the input image \e img
	 * until it gets to the underlying pure imglib2.Img.
	 */
	@SuppressWarnings("unchecked")
	private static <Q>
	Img<Q> getUnderlyingImg(final Img<Q> img)
	{
		if (img instanceof Dataset)
			return (Img<Q>) getUnderlyingImg( ((Dataset)img).getImgPlus() );
		else if (img instanceof WrappedImg)
			return getUnderlyingImg( ((WrappedImg<Q>)img).getImg() );
		else
			return img;
	}

	@SuppressWarnings("rawtypes") // use raw type because of insufficient support of reflexive types in java
	private static
	NativeType createVoxelType(String typeStr)
	{
		for(Class<? extends NativeType> aClass : SUPPORTED_VOXEL_CLASSES)
			if(typeStr.startsWith(aClass.getSimpleName()))
				try {
					return aClass.newInstance();
				} catch (InstantiationException | IllegalAccessException e) {
					throw new RuntimeException(e);
				}
		throw new IllegalArgumentException("Unsupported voxel type, sorry.");
	}

	private static <T extends NativeType<T>>
	Img<T> createImg(int[] dims, String backendStr, T type)
	{
		if (backendStr.startsWith("ArrayImg"))
			return new ArrayImgFactory<T>().create(dims, type);
		if (backendStr.startsWith("PlanarImg"))
			return new PlanarImgFactory<T>().create(dims, type);
		if (backendStr.startsWith("CellImg"))
			return new CellImgFactory<T>().create(dims, type);
		throw new RuntimeException("Unsupported image backend type, sorry.");
	}
}
