/*
 * Copyright 2009 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.zxing.web;

import com.google.gwt.canvas.client.Canvas;
import com.google.gwt.canvas.dom.client.CanvasPixelArray;
import com.google.gwt.canvas.dom.client.Context2d;
import com.google.gwt.canvas.dom.client.ImageData;
import com.google.zxing.LuminanceSource;

/**
 * This LuminanceSource implementation is meant for html canvas.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 * @author Sean Owen
 * @author code@elektrowolle.de (Wolfgang Jung)
 * gwt implementation: Janusz Białobrzewski
 */
public final class CanvasLuminanceSource extends LuminanceSource
{

    /**
     * Number of colors at each location in the array.
     * 
     * Because image data is stored as RGBA, this is 4.
     */
    private static final int NUM_COLORS = 4;

    /**
     * Offsets for each color use RGBA ordering.
     */
    private static final int OFFSET_RED = 0;
    private static final int OFFSET_GREEN = 1;
    private static final int OFFSET_BLUE = 2;
    private static final int OFFSET_ALPHA = 3;

    private final Canvas image;
    private final ImageData imagedata;
    private final int left;
    private final int top;

    public CanvasLuminanceSource(Canvas image, boolean inverse)
    {
        this(image, 0, 0, image.getCoordinateSpaceWidth(), image.getCoordinateSpaceHeight(), inverse);
    }

    public CanvasLuminanceSource(Canvas aImage, int left, int top, int width, int height, boolean inverse)
    {
        super(width, height);

        int sourceWidth = aImage.getCoordinateSpaceWidth();
        int sourceHeight = aImage.getCoordinateSpaceHeight();
        if(left + width > sourceWidth || top + height > sourceHeight)
        {
            throw new IllegalArgumentException("Crop rectangle does not fit within image data.");
        }

        this.image = Canvas.createIfSupported();
        this.image.setPixelSize(sourceWidth, sourceHeight);
        this.image.setCoordinateSpaceHeight(sourceHeight);
        this.image.setCoordinateSpaceWidth(sourceWidth);

        imagedata = aImage.getContext2d().getImageData(left, top, width, height);
        CanvasPixelArray data = imagedata.getData();

        for (int i = 0; i < data.getLength(); i += NUM_COLORS)
        {
            if((data.get(i + 3) & 0xFF) == 0)
            {
                // The color of fully-transparent pixels is irrelevant. They are
                // often, technically, fully-transparent
                // black (0 alpha, and then 0 RGB). They are often used, of
                // course as the "white" area in a
                // barcode image. Force any such pixel to be white:
                int avg = 0xFF;
                if(inverse)
                {
                    avg = 0;
                }
                data.set(i + OFFSET_RED, avg);
                data.set(i + OFFSET_GREEN, avg);
                data.set(i + OFFSET_BLUE, avg);
                data.set(i + OFFSET_ALPHA, avg);
            }
            else
            {
                int avg = (data.get(i + OFFSET_RED) + data.get(i + OFFSET_GREEN) + data.get(i
                    + OFFSET_BLUE)) / 3;
                if(inverse)
                {
                    avg = 0xFF - avg;
                }
                data.set(i + OFFSET_RED, avg);
                data.set(i + OFFSET_GREEN, avg);
                data.set(i + OFFSET_BLUE, avg);
            }
        }

        this.image.getContext2d().putImageData(imagedata, left, top);
        this.left = left;
        this.top = top;
    }

    @Override
    public byte[] getRow(int y, byte[] row)
    {
        if(y < 0 || y >= getHeight())
        {
            throw new IllegalArgumentException("Requested row is outside the image: " + y);
        }
        int width = getWidth();
        if(row == null || row.length < width)
        {
            row = new byte[width];
        }

        int yoffset = top + y;
        for (int i = 0; i < width; i++)
        {
            row[i] = (byte) imagedata.getRedAt(left + i, yoffset);
        }

        return row;
    }

    @Override
    public byte[] getMatrix()
    {
        int width = getWidth();
        int height = getHeight();
        int area = width * height;
        byte[] matrix = new byte[area];
        for (int i = 0; i < width; i++)
            for (int j = 0; j < height; j++)
            {
                int pixelInt = imagedata.getRedAt(left + i, top + j);
                byte pixelByte = (byte) pixelInt;
                matrix[i + j * width] = pixelByte;
            }
        return matrix;
    }

    @Override
    public boolean isCropSupported()
    {
        return true;
    }

    @Override
    public LuminanceSource crop(int left, int top, int width, int height)
    {
        return new CanvasLuminanceSource(image, this.left + left, this.top + top, width, height, false);
    }

    /**
     * This is always true, since the image is a gray-scale image.
     *
     * @return true
     */
    @Override
    public boolean isRotateSupported()
    {
        return true;
    }

    @Override
    public LuminanceSource rotateCounterClockwise()
    {
        int sourceWidth = image.getCoordinateSpaceWidth();
        int sourceHeight = image.getCoordinateSpaceHeight();

        // Note width/height are flipped since we are rotating 90 degrees.
        Canvas rotatedImage = Canvas.createIfSupported();
        rotatedImage.setCoordinateSpaceWidth(sourceWidth);
        rotatedImage.setCoordinateSpaceHeight(sourceHeight);
        
        Context2d ctx = rotatedImage.getContext2d();
        // Rotate 90 degrees counterclockwise.
        ctx.transform(0.0, -1.0, 1.0, 0.0, 0.0, sourceWidth);
        ctx.putImageData(imagedata, sourceWidth, sourceHeight);
        // Maintain the cropped region, but rotate it too.
        int width = getWidth();
        return new CanvasLuminanceSource(rotatedImage, top, sourceWidth - (left + width),
            getHeight(), width, false);
    }

    @Override
    public LuminanceSource rotateCounterClockwise45()
    {
        int sourceWidth = image.getCoordinateSpaceWidth();
        int sourceHeight = image.getCoordinateSpaceHeight();

        int width = getWidth();
        int height = getHeight();

        int oldCenterX = left + width / 2;
        int oldCenterY = top + height / 2;

        Canvas rotatedImage = Canvas.createIfSupported();
        int sourceDimension = Math.max(sourceWidth, sourceHeight);
        rotatedImage.setCoordinateSpaceWidth(sourceDimension);
        rotatedImage.setCoordinateSpaceHeight(sourceDimension);
        
        Context2d ctx = rotatedImage.getContext2d();
        ctx.translate(oldCenterX, oldCenterY);
        // Rotate 45 degrees counterclockwise.
        ctx.rotate(-45 * Math.PI / 180);
        ctx.translate(-oldCenterX, -oldCenterY);
        ctx.putImageData(imagedata, sourceWidth, sourceHeight);


        int halfDimension = Math.max(width, height) / 2;
        int newLeft = Math.max(0, oldCenterX - halfDimension);
        int newTop = Math.max(0, oldCenterY - halfDimension);
        int newRight = Math.min(sourceDimension - 1, oldCenterX + halfDimension);
        int newBottom = Math.min(sourceDimension - 1, oldCenterY + halfDimension);

        return new CanvasLuminanceSource(rotatedImage, newLeft, newTop, newRight - newLeft,
            newBottom - newTop, false);
    }

}
