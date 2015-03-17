package com.esri;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.MultipleGradientPaint;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Serializable;

/**
 */
public class ColorMapper implements Serializable
{
    public Color[] colors;

    public void construct() throws IOException
    {
        final BufferedImage bi = createGradientImage(
                new Color(0xFF, 0xFF, 0x00, 128),
                new Color(0xFF, 0x7F, 0x00, 128),
                new Color(0xFF, 0x00, 0x00, 128)
                                                    );
        colors = new Color[256];
        for (int i = 0; i < 256; i++)
        {
            colors[i] = new Color(bi.getRGB(i, 0), true);
        }
    }

    private BufferedImage createGradientImage(Color... colors)
    {
        final float[] fractions = new float[colors.length];

        final float step = 1.0F / colors.length;

        for (int i = 0; i < colors.length; i++)
        {
            fractions[i] = i * step;
        }

        final LinearGradientPaint gradient = new LinearGradientPaint(0, 0, 256, 1, fractions, colors, MultipleGradientPaint.CycleMethod.REPEAT);
        final BufferedImage bi = new BufferedImage(256, 1, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D g = bi.createGraphics();
        try
        {
            g.setPaint(gradient);
            g.fillRect(0, 0, 256, 1);
        }
        finally
        {
            g.dispose();
        }
        return bi;
    }

    public Color getColor(final int index)
    {
        return colors[index];
    }

    public Color getColor(final float val, final float min, final float del)
    {
        return getColor((int) (255 * (val - min) / del));
    }
}
