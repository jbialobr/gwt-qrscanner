package com.google.zxing.web;

import java.util.ArrayList;
import java.util.List;

import com.google.gwt.canvas.client.Canvas;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.dom.client.Element;
import com.google.gwt.media.client.Video;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.zxing.Binarizer;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.Reader;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

public class ScannerWidget extends FlowPanel
{

    private Video video = Video.createIfSupported();
    private Canvas canvas = Canvas.createIfSupported();
    private QRCodeReader qrReader = new QRCodeReader();
//    private MultiFormatOneDReader oneDReader = new MultiFormatOneDReader(null); 
    private List<Reader> readers = new ArrayList<Reader>();
    private double lastScanTime;
    private int scanInterval = 300;
    private AsyncCallback<Result> callback;
    private Timer scanTimer;
    private int snapImageMaxSize = -1;
    private boolean active = true;
    private JavaScriptObject videoStream;
    private JavaScriptObject videoStreamProvider;

    public ScannerWidget(AsyncCallback<Result> callback, JavaScriptObject videoStreamProvider)
    {
        this.callback = callback;
        this.videoStreamProvider = videoStreamProvider;
        //readers.add(oneDReader);
        readers.add(qrReader);
        createScanTimer();
        add(video);
        video.setStyleName("qrPreviewVideo");
        video.setAutoplay(true);
    }

    private void createScanTimer()
    {
        scanTimer = new Timer()
        {

            @Override
            public void run()
            {
                scan();
            }
        };

    }

    private BinaryBitmap createSnapImage()
    {
        int w, h;
        w = video.getVideoWidth();
        h = video.getVideoHeight();
        if(w > 0 && h > 0)
        {
            if(snapImageMaxSize > 0)
            {
                if(w > h)
                {
                    if(snapImageMaxSize < w)
                    {
                        h = h * snapImageMaxSize / w;
                        w = snapImageMaxSize;
                    }
                }
                else
                {
                    if(snapImageMaxSize < h)
                    {
                        w = w * snapImageMaxSize / h;
                        h = snapImageMaxSize;
                    }
                }
            }
            canvas.setCoordinateSpaceWidth(w);
            canvas.setCoordinateSpaceHeight(h);
            canvas.getContext2d().drawImage(video.getVideoElement(), 0, 0, w, h);

            CanvasLuminanceSource lsource = new CanvasLuminanceSource(canvas);
            Binarizer binarizer = new HybridBinarizer(lsource);
            BinaryBitmap snapImage = new BinaryBitmap(binarizer);
            return snapImage;
        }
        return null;
    }

    private void startScanning()
    {
        if(isScanning())
            scanTimer.schedule(scanInterval);
    }

    public void stopScanning()
    {
        active = false;
    }

    public void resumeScanning()
    {
        active = true;
        startScanning();
    }

    private void videoAttached()
    {
        startScanning();
    }

    private void scan()
    {
        if(!isScanning())
            return;

        try
        {
            BinaryBitmap bitmap = createSnapImage();
            if(bitmap != null)
            {
                for(Reader reader : readers)
                {
                    try
                    {
                        reader.reset();
                        Result result = reader.decode(bitmap);
                        onSuccess(result);
                        return;
                    }
                    catch(Exception e)
                    {
                        onError(e);
                    }
                }
            }
        }
        finally
        {
            startScanning();
        }
    }

    private void onSuccess(Result result)
    {
        callback.onSuccess(result);
    }

    private void onError(Exception e)
    {
        callback.onFailure(e);
    }
    
    private void reportError(String msg)
    {
        callback.onSuccess(new Result(msg, null, null, null));
    }

    public native void stopWebcam(ScannerWidget scanner) /*-{
        
if (scanner.@com.google.zxing.web.ScannerWidget::videoStream) {
  var stream = scanner.@com.google.zxing.web.ScannerWidget::videoStream;
  if (stream.stop)
  {
    stream.stop();
  } else if (stream.getTracks)
  {
    stream.getTracks().forEach(function (track)
    {
      track.stop();
    });
  }

  scanner.@com.google.zxing.web.ScannerWidget::videoStream = null;
}
    }-*/;

    public native void setWebcam(Element videoElement, ScannerWidget scanner) /*-{

function success(stream)
{
  scanner.@com.google.zxing.web.ScannerWidget::videoStream = stream;
  var v = videoElement;

  function removeControls(){
    v.removeAttribute('controls');
  }

  try
  {
    v.srcObject = stream;
    v.setAttribute('playsinline', true);
    v.setAttribute('controls', true);
    setTimeout(removeControls);
  } catch (ex)
  {
    v.src = $wnd.URL.createObjectURL(stream);
  }
  scanner.@com.google.zxing.web.ScannerWidget::videoAttached()();
}

function error(ex)
{
  var msg = ex.message
  scanner.@com.google.zxing.web.ScannerWidget::reportError(Ljava/lang/String;)(msg);
}

  var vsp = scanner.@com.google.zxing.web.ScannerWidget::videoStreamProvider;
  if(vsp)
  {
    vsp().then(success)['catch'](error);
    return;
  }


var n = $wnd.navigator;

if (n.mediaDevices && n.mediaDevices.getUserMedia)
{
  n.mediaDevices.getUserMedia({
    video: {
      facingMode: "environment"
    },
    audio: false
  }).then(success)['catch'](error);
} 
else
{
  var constraints = {
    audio: false,
    video: true
  };

  if (n.getUserMedia)
  {
    n.getUserMedia(constraints, success, error);
  } else if (n.webkitGetUserMedia)
  {
    n.webkitGetUserMedia(constraints, success, error);
  } else if (n.mozGetUserMedia)
  {
    n.mozGetUserMedia(constraints, success, error);
  }
}
    }-*/;

    public int getScanInterval()
    {
        return scanInterval;
    }

    public void setScanInterval(int scanInterval)
    {
        this.scanInterval = scanInterval;
    }

    public int getSnapImageMaxSize()
    {
        return snapImageMaxSize;
    }

    public void setSnapImageMaxSize(int snapImageMaxSize)
    {
        this.snapImageMaxSize = snapImageMaxSize;
    }

    @Override
    protected void onAttach()
    {
        super.onAttach();
        video.setSrc("");
        try
        {
            setWebcam(video.getElement(), this);
        }
        catch(Throwable t)
        {
            reportError(t.getMessage());
        }
    }

    @Override
    protected void onDetach()
    {
        super.onDetach();
        stopWebcam(this);
    }

    public boolean isActive()
    {
        return active;
    }

    public boolean isScanning()
    {
        return isActive() && isAttached();
    }

}
