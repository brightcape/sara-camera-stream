/*****************************************************************************
*                                                                            *
*  OpenNI 2.x Alpha                                                          *
*  Copyright (C) 2012 PrimeSense Ltd.                                        *
*                                                                            *
*  This file is part of OpenNI.                                              *
*                                                                            *
*  Licensed under the Apache License, Version 2.0 (the "License");           *
*  you may not use this file except in compliance with the License.          *
*  You may obtain a copy of the License at                                   *
*                                                                            *
*      http://www.apache.org/licenses/LICENSE-2.0                            *
*                                                                            *
*  Unless required by applicable law or agreed to in writing, software       *
*  distributed under the License is distributed on an "AS IS" BASIS,         *
*  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  *
*  See the License for the specific language governing permissions and       *
*  limitations under the License.                                            *
*                                                                            *
*****************************************************************************/
package com.tools.niviewer;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

import org.openni.Device;
import org.openni.OpenNI;
import org.openni.SensorType;
import org.openni.VideoFrameRef;
import org.openni.VideoMode;
import org.openni.VideoStream;
import org.openni.PixelFormat;
import org.openni.android.OpenNIView;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.AdapterView.OnItemSelectedListener;

public class StreamView extends RelativeLayout {

	private static final String TAG = "StreamView";
	private Thread mFrameLoopThread;
	private boolean mShouldRun = true;
	private Device mDevice;
	private VideoStream mStream;
	private Spinner mSensorSpinner;
	private List<SensorType> mDeviceSensors;
	private List<VideoMode> mStreamVideoModes;
	private Spinner mVideoModeSpinner;
	private OpenNIView mFrameView;
	private TextView mStatusLine;
	private CheckBox mTransmit;
	private boolean transmit = false;

    private static SensorType[] SENSORS = { SensorType.DEPTH, SensorType.COLOR };
	private static CharSequence[] SENSOR_NAMES = { "Depth", "Color" };

	public StreamView(Context context) {
		super(context);
		initialize(context);
	}

	public StreamView(Context context, AttributeSet attrs) {
		super(context, attrs);
		initialize(context);
	}

	public StreamView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		initialize(context);
	}

	public VideoStream getStream() {
		return mStream;
	}

	private void initialize(Context context) {
		View.inflate(context, R.layout.stream_view, this);
		
		if (!isInEditMode()) {
			mSensorSpinner = (Spinner) findViewById(R.id.spinnerSensor);
			mVideoModeSpinner = (Spinner) findViewById(R.id.spinnerVideoMode);
			mFrameView = (OpenNIView) findViewById(R.id.frameView);
			mTransmit = (CheckBox) findViewById(R.id.checkboxTransmit);
			mStatusLine = (TextView) findViewById(R.id.status_line);

			mTransmit.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
				@Override
				public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
					transmit = b;
				}
			});

			mSensorSpinner.setEnabled(false);
			mSensorSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
				@Override
				public void onItemSelected(AdapterView<?> parent, View view,
						int position, long id) {
					onSensorSelected(position);
				}
	
				@Override
				public void onNothingSelected(AdapterView<?> parent) {
				}
			});
	
			mVideoModeSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
				@Override
				public void onItemSelected(AdapterView<?> parent, View view,
						int position, long id) {
					onVideoModeSelected(position);
				}
	
				@Override
				public void onNothingSelected(AdapterView<?> parent) {
				}
			});
		}
	}
	
	public void setDevice(Device device, int sensor) {
		mDevice = device;
		mDeviceSensors = new ArrayList<SensorType>();

		List<CharSequence> sensors = new ArrayList<CharSequence>();
		
		for (int i = 0; i < SENSORS.length; ++i) {
			if (mDevice.hasSensor(SENSORS[i])) {
				sensors.add(SENSOR_NAMES[i]);
				mDeviceSensors.add(SENSORS[i]);
			}
		}
		
		ArrayAdapter<CharSequence> sensorsAdapter = new ArrayAdapter<CharSequence>(getContext(),
				android.R.layout.simple_spinner_item, sensors);
		sensorsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		mSensorSpinner.setAdapter(sensorsAdapter);
		
		mSensorSpinner.setSelection(sensor);
	}

	public void stop() {
		mShouldRun = false;
		
		while (mFrameLoopThread != null) {
			try {
				mFrameLoopThread.join();
				mFrameLoopThread = null;
				break;
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		if (mStream != null) {
			mStream.stop();
		}
		
		mFrameView.clear();
		mStatusLine.setText(R.string.waiting_for_frames);
	}
	
	private void onSensorSelected(int pos) {
		try {
			stop();
			
			if (mStream != null) {
				mStream.destroy();
				mStream = null;
			}
		
			SensorType sensor = mDeviceSensors.get(pos);
			mStream = VideoStream.create(mDevice, sensor);
			List<CharSequence> videoModesNames = new ArrayList<CharSequence>();

			VideoMode currentMode = null;
			mStreamVideoModes = mStream.getSensorInfo().getSupportedVideoModes();
			for (int i = 0; i < mStreamVideoModes.size(); ++i) {
				VideoMode mode = mStreamVideoModes.get(i);
				if(mode.getResolutionX() == 640 && mode.getResolutionY() == 480 && mode.getFps() == 30) {
					if((sensor == SensorType.DEPTH && mode.getPixelFormat() == PixelFormat.DEPTH_1_MM) || (sensor == SensorType.COLOR && mode.getPixelFormat() == PixelFormat.RGB888))
					{
						currentMode = mode;
					}
				}

				Log.i(TAG,"X x Y = "+mode.getResolutionX()+"x"+mode.getResolutionY());
				Log.i(TAG,"FPS = "+mode.getFps());
				Log.i(TAG,"pixelFormat = "+pixelFormatToName(mode.getPixelFormat()));

				videoModesNames.add(String.format("%d x %d @ %d FPS (%s)",
	                mode.getResolutionX(),
	                mode.getResolutionY(), 
	                mode.getFps(),
	                pixelFormatToName(mode.getPixelFormat())));
			}



			ArrayAdapter<CharSequence> videoModesAdapter = new ArrayAdapter<CharSequence>(getContext(),
					android.R.layout.simple_spinner_item, videoModesNames);
			videoModesAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
			mVideoModeSpinner.setAdapter(videoModesAdapter);
	
			// use default mode
			if(currentMode == null){
				currentMode = mStream.getVideoMode();
			}

			int selected = mStreamVideoModes.indexOf(currentMode);
			if (selected == -1) {
				selected = 0;
			}

			mStream.setMirroringEnabled(false);
			mVideoModeSpinner.setSelection(selected);
			
			if (sensor == SensorType.DEPTH) {
				mFrameView.setBaseColor(Color.WHITE);
			}
			
		} catch (RuntimeException e) {
			showAlert("Failed to switch to stream: " + e.getMessage());
		}
	}
	
    private CharSequence pixelFormatToName(PixelFormat format) {
        switch (format) {
            case DEPTH_1_MM:    return "1 mm";
            case DEPTH_100_UM:  return "100 um";
            case SHIFT_9_2:     return "9.2";
            case SHIFT_9_3:     return "9.3";
            case RGB888:        return "RGB";
            case GRAY8:         return "Gray8";
            case GRAY16:        return "Gray16";
            case YUV422:		return "YUV422";
            case YUYV:			return "YUYV";
            default:            return "UNKNOWN";
        }
    }
	
	private void onVideoModeSelected(int pos) {
		try {
			stop();

			Log.e(TAG,""+mStreamVideoModes.get(pos).getPixelFormat().toNative());
			Log.e(TAG,""+mStreamVideoModes.get(pos).getResolutionX());
			Log.e(TAG,""+mStreamVideoModes.get(pos).getResolutionY());

			mStream.setVideoMode(mStreamVideoModes.get(pos));

			mStream.start();
		} catch (RuntimeException e) {
			e.printStackTrace();
			showAlert("Failed to switch to video mode: " + e.getMessage());
		}

		mShouldRun = true;
		mFrameLoopThread = new Thread() {
			@Override
			public void run() {
				List<VideoStream> streams = new ArrayList<VideoStream>();
				streams.add(mStream);
				long lastTime = System.nanoTime();
				long frameCount = 0;	
				int fps = 0;
				long frameIndex = 0;
				long frameIndexPre = 0;

				SocketServer socketServer = SocketServer.getServer();

				while (mShouldRun) {
						VideoFrameRef frame = null;
						try {
							OpenNI.waitForAnyStream(streams, 100);
							frame = mStream.readFrame();

							if(transmit)
							{
								ByteBuffer frameByteBuffer = frame.getData();

								int type = mStream.getSensorType() == SensorType.COLOR ? 0 : 1;
								int index = frame.getFrameIndex();
								int width = frame.getWidth();
								int height = frame.getHeight();
								int length = frameByteBuffer.capacity();

								byte[] frameData = CompressionUtils.compress(frameByteBuffer);

								ByteBuffer headerByteBuffer = ByteBuffer.allocate(24 + frameData.length);
								headerByteBuffer.putInt(type);
								headerByteBuffer.putInt(index);
								headerByteBuffer.putInt(width);
								headerByteBuffer.putInt(height);
								headerByteBuffer.putInt(length);
								headerByteBuffer.putInt(frameData.length);
								headerByteBuffer.put(frameData);

								headerByteBuffer.rewind();

								byte[] headerByteArray = new byte[headerByteBuffer.capacity()];
								headerByteBuffer.get(headerByteArray, 0, headerByteArray.length);

								if(mStream.getSensorType() == SensorType.COLOR){
									socketServer.offerColorFrame(headerByteArray);
								}

								if(mStream.getSensorType() == SensorType.DEPTH){
									socketServer.offerDepthFrame(headerByteArray);
								}
							}

							// Request rendering of the current OpenNI frame
							mFrameView.update(frame);

							frame.release();

							if(0 == frameIndexPre) {
								frameIndexPre = frame.getFrameIndex();
							}

							frameCount++;
							if (frameCount == 30) {
								long now = System.nanoTime();
								long diff = now - lastTime;
								frameIndex = frame.getFrameIndex();
								long count = frameIndex - frameIndexPre;
								frameIndexPre = frameIndex;
								fps = (int)(1e9 * count / diff);
								frameCount = 0;
								lastTime = now;
							}

							updateLabel(String.format("Frame Index: %,d | Timestamp: %,d | FPS: %d", frame.getFrameIndex(), frame.getTimestamp(), fps));

						} catch (TimeoutException e) {
							e.printStackTrace();
						} catch (Exception e) {
							Log.e(TAG, "Failed reading frame: " + e.getMessage());

						}
					}

			};
		};
		
		mFrameLoopThread.setName("Frame Thread");
		mFrameLoopThread.start();


	}

	private void showAlert(String message) {
		AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
		builder.setMessage(message);
		builder.show();
	}
	
	private void updateLabel(final String message) {
		post(new Runnable() {
			public void run() {
				mStatusLine.setText(message);								
			}
		});
	}
}
