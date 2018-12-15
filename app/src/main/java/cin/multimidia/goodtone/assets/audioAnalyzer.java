package cin.multimidia.goodtone.assets;

import android.content.res.AssetManager;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;

public class audioAnalyzer {

    private File audioInput;
    private String fileType;
    private int fileSize;
    private int channelCount;
    private int averageBitRate;
    private int sampleRate;
    private int sampleNumber;
    private ByteBuffer decodedBytes;
    private ShortBuffer decodedSamples;

    private double percentage;

    private boolean stopper;

    private boolean fileRead;
    private boolean percantageCalculated;
    private boolean audioModified;

    private Classifier mClassifiers;

    public audioAnalyzer(File audioInput){
        this.audioInput = audioInput;
    }

    public void stopOperation() {
        stopper = true;
    }

    public boolean isFileRead() {
        return fileRead;
    }

    public String getClassifier() {
        return mClassifiers.name();
    }

    public double getPercentage() {
        short[] auxiliary = decodedSamples.array();
        float[] result = new float[auxiliary.length];
        float multiplier = 1 / (2^16);
        for (int i =0; i < auxiliary.length; i++) {
            result[i] = auxiliary[i] * multiplier;
        }
        final Classification res = mClassifiers.recognize(result);
        return res.getConf();
    }

    public void loadModel(final AssetManager assetManager) {
        //The Runnable interface is another way in which you can implement multi-threading other than extending the
        // //Thread class due to the fact that Java allows you to extend only one class. Runnable is just an interface,
        // //which provides the method run.
        // //Threads are implementations and use Runnable to call the method run().
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    //add 2 classifiers to our classifier arraylist
                    //the tensorflow classifier and the keras classifier
                    mClassifiers =
                            TensorFlowClassifier.create(assetManager, "TensorFlow",
                                    "opt_emotions.pb", "labels.txt", decodedSamples.array().length,
                                    "input", "output", true);
                } catch (final Exception e) {
                    //if they aren't found, throw an error!
                    throw new RuntimeException("Error initializing classifiers!", e);
                }
            }
        }).start();
    }

    private class fileReader extends Thread {

        @Override
        public void run() {

            try {
                audioAnalyzer.this.fileRead = false;
                audioAnalyzer.this.percantageCalculated = false;
                audioAnalyzer.this.audioModified = false;
                audioAnalyzer.this.fileType = null;
                audioAnalyzer.this.fileSize = 0;
                audioAnalyzer.this.channelCount = 0;
                audioAnalyzer.this.averageBitRate = 0;
                audioAnalyzer.this.sampleRate = 0;
                audioAnalyzer.this.sampleNumber = 0;
                audioAnalyzer.this.decodedBytes = null;
                audioAnalyzer.this.decodedSamples = null;

                MediaExtractor extractor = new MediaExtractor();
                MediaFormat format = null;
                int i;

                String[] components = audioAnalyzer.this.audioInput.getPath().split("\\.");
                audioAnalyzer.this.fileType = components[components.length - 1];
                audioAnalyzer.this.fileSize = (int) audioAnalyzer.this.audioInput.length();
                extractor.setDataSource(audioAnalyzer.this.audioInput.getPath());
                int numTracks = extractor.getTrackCount();
                // find and select the first audio track present in the file.
                for (i = 0; i < numTracks; i++) {
                    format = extractor.getTrackFormat(i);
                    if (format.getString(MediaFormat.KEY_MIME).startsWith("audio/")) {
                        extractor.selectTrack(i);
                        break;
                    }
                }
                if (i == numTracks) {
                    throw new Exception("No audio track found in " + audioAnalyzer.this.audioInput);
                }
                audioAnalyzer.this.channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                audioAnalyzer.this.sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                // Expected total number of samples per channel.
                int expectedNumSamples =
                        (int) ((format.getLong(MediaFormat.KEY_DURATION) / 1000000.f) * audioAnalyzer.this.sampleRate + 0.5f);

                MediaCodec codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
                codec.configure(format, null, null, 0);
                codec.start();

                int decodedSamplesSize = 0;  // size of the output buffer containing decoded samples.
                byte[] decodedSamples = null;
                ByteBuffer[] inputBuffers = codec.getInputBuffers();
                ByteBuffer[] outputBuffers = codec.getOutputBuffers();
                int sample_size;
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                long presentation_time;
                int tot_size_read = 0;
                boolean done_reading = false;

                // Set the size of the decoded samples buffer to 1MB (~6sec of a stereo stream at 44.1kHz).
                // For longer streams, the buffer size will be increased later on, calculating a rough
                // estimate of the total size needed to store all the samples in order to resize the buffer
                // only once.
                audioAnalyzer.this.decodedBytes = ByteBuffer.allocate(1 << 20);
                Boolean firstSampleData = true;
                while (true) {
                    // read data from file and feed it to the decoder input buffers.
                    int inputBufferIndex = codec.dequeueInputBuffer(100);
                    if (!done_reading && inputBufferIndex >= 0) {
                        sample_size = extractor.readSampleData(inputBuffers[inputBufferIndex], 0);
                        if (firstSampleData
                                && format.getString(MediaFormat.KEY_MIME).equals("audio/mp4a-latm")
                                && sample_size == 2) {
                            // For some reasons on some devices (e.g. the Samsung S3) you should not
                            // provide the first two bytes of an AAC stream, otherwise the MediaCodec will
                            // crash. These two bytes do not contain music data but basic info on the
                            // stream (e.g. channel configuration and sampling frequency), and skipping them
                            // seems OK with other devices (MediaCodec has already been configured and
                            // already knows these parameters).
                            extractor.advance();
                            tot_size_read += sample_size;
                        } else if (sample_size < 0) {
                            // All samples have been read.
                            codec.queueInputBuffer(
                                    inputBufferIndex, 0, 0, -1, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            done_reading = true;
                        } else {
                            presentation_time = extractor.getSampleTime();
                            codec.queueInputBuffer(inputBufferIndex, 0, sample_size, presentation_time, 0);
                            extractor.advance();
                            tot_size_read += sample_size;
                            if (audioAnalyzer.this.stopper) {
                                // We are asked to stop reading the file. Returning immediately. The
                                // SoundFile object is invalid and should NOT be used afterward!
                                extractor.release();
                                extractor = null;
                                codec.stop();
                                codec.release();
                                codec = null;
                                audioAnalyzer.this.stopper = false;
                                return;
                            }
                        }
                        firstSampleData = false;
                    }

                    // Get decoded stream from the decoder output buffers.
                    int outputBufferIndex = codec.dequeueOutputBuffer(info, 100);
                    if (outputBufferIndex >= 0 && info.size > 0) {
                        if (decodedSamplesSize < info.size) {
                            decodedSamplesSize = info.size;
                            decodedSamples = new byte[decodedSamplesSize];
                        }
                        outputBuffers[outputBufferIndex].get(decodedSamples, 0, info.size);
                        outputBuffers[outputBufferIndex].clear();
                        // Check if buffer is big enough. Resize it if it's too small.
                        if (audioAnalyzer.this.decodedBytes.remaining() < info.size) {
                            // Getting a rough estimate of the total size, allocate 20% more, and
                            // make sure to allocate at least 5MB more than the initial size.
                            int position = audioAnalyzer.this.decodedBytes.position();
                            int newSize = (int) ((position * (1.0 * audioAnalyzer.this.fileSize / tot_size_read)) * 1.2);
                            if (newSize - position < info.size + 5 * (1 << 20)) {
                                newSize = position + info.size + 5 * (1 << 20);
                            }
                            ByteBuffer newDecodedBytes = null;
                            // Try to allocate memory. If we are OOM, try to run the garbage collector.
                            int retry = 10;
                            while (retry > 0) {
                                try {
                                    newDecodedBytes = ByteBuffer.allocate(newSize);
                                    break;
                                } catch (OutOfMemoryError oome) {
                                    // setting android:largeHeap="true" in <application> seem to help not
                                    // reaching this section.
                                    retry--;
                                }
                            }
                            if (retry == 0) {
                                // Failed to allocate memory... Stop reading more data and finalize the
                                // instance with the data decoded so far.
                                break;
                            }
                            //ByteBuffer newDecodedBytes = ByteBuffer.allocate(newSize);
                            audioAnalyzer.this.decodedBytes.rewind();
                            newDecodedBytes.put(audioAnalyzer.this.decodedBytes);
                            audioAnalyzer.this.decodedBytes = newDecodedBytes;
                            audioAnalyzer.this.decodedBytes.position(position);
                        }
                        audioAnalyzer.this.decodedBytes.put(decodedSamples, 0, info.size);
                        codec.releaseOutputBuffer(outputBufferIndex, false);
                    } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                        outputBuffers = codec.getOutputBuffers();
                    } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        // Subsequent data will conform to new format.
                        // We could check that codec.getOutputFormat(), which is the new output format,
                        // is what we expect.
                    }
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                            || (audioAnalyzer.this.decodedBytes.position() / (2 * audioAnalyzer.this.channelCount)) >= expectedNumSamples) {
                        // We got all the decoded data from the decoder. Stop here.
                        // Theoretically dequeueOutputBuffer(info, ...) should have set info.flags to
                        // MediaCodec.BUFFER_FLAG_END_OF_STREAM. However some phones (e.g. Samsung S3)
                        // won't do that for some files (e.g. with mono AAC files), in which case subsequent
                        // calls to dequeueOutputBuffer may result in the application crashing, without
                        // even an exception being thrown... Hence the second check.
                        // (for mono AAC files, the S3 will actually double each sample, as if the stream
                        // was stereo. The resulting stream is half what it's supposed to be and with a much
                        // lower pitch.)
                        break;
                    }
                }
                audioAnalyzer.this.sampleNumber = audioAnalyzer.this.decodedBytes.position() / (audioAnalyzer.this.channelCount * 2);  // One sample = 2 bytes.
                audioAnalyzer.this.decodedBytes.rewind();
                audioAnalyzer.this.decodedBytes.order(ByteOrder.LITTLE_ENDIAN);
                audioAnalyzer.this.decodedSamples = audioAnalyzer.this.decodedBytes.asShortBuffer();
                audioAnalyzer.this.averageBitRate = (int) ((audioAnalyzer.this.fileSize * 8) * ((float) audioAnalyzer.this.sampleRate / audioAnalyzer.this.sampleNumber) / 1000);

                extractor.release();
                extractor = null;
                codec.stop();
                codec.release();
                codec = null;
                audioAnalyzer.this.decodedSamples.rewind();
                audioAnalyzer.this.fileRead = true;
            } catch (Exception e) {
                e.printStackTrace();
            }

        }

    }

    public void generateFileSamples() {

        fileReader fileReader = new fileReader();
        fileReader.run();

    }

}
