package com.esri;

import java.io.Serializable;

public class HexCell implements Serializable
{
    final double[] x = new double[7];
    final double[] y = new double[7];

    public HexCell(double size)
    {
        for (int i = 0; i < 7; i++)
        {
            final double angle = Math.PI * ((i % 6) + 0.5) / 3.0;
            x[i] = size * Math.cos(angle);
            y[i] = size * Math.sin(angle);
        }
    }
}
