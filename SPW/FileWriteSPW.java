package SPW;

/*
 * #%L
 * OME Bio-Formats package for reading and converting biological file formats.
 * %%
 * Copyright (C) 2005 - 2014 Open Microscopy Environment:
 *   - Board of Regents of the University of Wisconsin-Madison
 *   - Glencoe Software, Inc.
 *   - University of Dundee
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the 
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public 
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.common.services.ServiceFactory;
import loci.formats.CoreMetadata;
import loci.formats.FormatException;
import loci.formats.FormatTools;
import loci.formats.ImageWriter;
import loci.formats.meta.IMetadata;
import loci.formats.ome.OMEXMLMetadata;
import loci.formats.services.OMEXMLService;
import loci.formats.MetadataTools;
import loci.common.DataTools;

import ome.xml.model.enums.DimensionOrder;
import ome.xml.model.enums.EnumerationException;
import ome.xml.model.enums.PixelType;
import ome.xml.model.primitives.PositiveInteger;
import ome.xml.model.primitives.NonNegativeInteger;
import ome.xml.model.enums.NamingConvention;

/**
 * Example class that shows how to export raw pixel data to OME-TIFF as a Plate using
 * Bio-Formats version 5.0.3 or later.
 */
public class FileWriteSPW {
  
  private final int pixelType = FormatTools.UINT16;
  private int rows;
  private int cols;
  private int width;
  private int height;
  private int sizet;
  boolean initializationSuccess = false;
  
  private ArrayList<String> delays = null;
  
  private double[] exposureTimes = null;
  
  /** The file writer. */
  private ImageWriter writer = null;

  /** The name of the current output file. */
  private final String outputFile;
  

  /**
   * Construct a new FileWriteSPW that will save to the specified file.
   *
   * @param outputFile the file to which we will export
   */
  public FileWriteSPW(String outputFile) {
    this.outputFile = outputFile;    
    File file = new File(outputFile);
 
    // delete file if it exists
    // NB deleting old files seems to be critical 
    if (file.exists())  {
      file.delete();
    }
  }
  
  public boolean init( int[][] nFov, int sizeX, int  sizeY, int sizet, ArrayList<String> delays, double[] exposureTimes )  {
    
    this.exposureTimes = exposureTimes;
    
    initializationSuccess = init(nFov, sizeX, sizeY, sizet, delays);
    
    return initializationSuccess;
  }
  
  public boolean init( int[][] nFov, int sizeX, int  sizeY, int sizet, ArrayList<String> delays )  {
    this.rows = nFov.length;
    this.cols = nFov[0].length;
    width = sizeX;
    this.height = sizeY;
    this.sizet = sizet;
    
    Exception exception = null;
    
    setupModulo(delays);
    
    IMetadata omexml = initializeMetadata(nFov);
    
    initializationSuccess = initializeWriter(omexml);
    
    return initializationSuccess;
    
  }
  
  public boolean init( int[][] nFov, int sizeX, int  sizeY )  {
    this.rows = nFov.length;
    this.cols = nFov[0].length;
    width = sizeX;
    this.height = sizeY;
    this.sizet = 1;
    
    Exception exception = null;
    
    IMetadata omexml = initializeMetadata(nFov);
    
    initializationSuccess = initializeWriter(omexml);
    
    return initializationSuccess;
    
  }

  /** Save a single byte plane of data.
   * @param plane  data
   * @param series  image no in plate
   * @param index t plane within image*/
  public void export(byte[] plane, int series, int index) {
    
    Exception exception = null;
    
    if (initializationSuccess) {
      if (series != writer.getSeries())  {
        try {
          writer.setSeries(series);
        } catch (FormatException e) {
          exception = e;
        }
      }
      savePlane( plane, index);
    }   //endif 
  }

  /** Save a single Short plane of data.
   * @param plane  data
   * @param series  image no in plate
   * @param index t plane within image*/
  public void export(short[] plane, int series, int index) {
    
    byte[] planeb = DataTools.shortsToBytes(plane, false);
    export(planeb, series, index);
           
  }
  
  /**
   * Set up the file writer.
   *
   * @param omexml the IMetadata object that is to be associated with the writer
   * @return true if the file writer was successfully initialized; false if an
   *   error occurred
   */
  private boolean initializeWriter(IMetadata omexml) {
    // create the file writer and associate the OME-XML metadata with it
    writer = new ImageWriter();
    writer.setMetadataRetrieve(omexml);

    Exception exception = null;
    try {
      writer.setId(outputFile);
    }
    catch (FormatException e) {
      exception = e;
    }
    catch (IOException e) {
      exception = e;
    }
    if (exception != null) {
      System.err.println("Failed to initialize file writer.");
      exception.printStackTrace();
    }
    return exception == null;
  }

  /**
   * Populate the minimum amount of metadata required to export a Plate.
   *
   */
  private IMetadata initializeMetadata(int[][] nFovs) {
    Exception exception = null;
    try {
      // create the OME-XML metadata storage object
      ServiceFactory factory = new ServiceFactory();
      OMEXMLService service = factory.getInstance(OMEXMLService.class);
      OMEXMLMetadata meta = service.createOMEXMLMetadata();
      //IMetadata meta = service.createOMEXMLMetadata();
      meta.createRoot();
      
      int plateIndex = 0;
      int series = 0;     // count of images
      int well = 0;
      
      // Create Minimal 2x2 Plate 
      meta.setPlateID(MetadataTools.createLSID("Plate", 0), 0);
      
      meta.setPlateRowNamingConvention(NamingConvention.LETTER, 0);
      meta.setPlateColumnNamingConvention(NamingConvention.NUMBER, 0);
      meta.setPlateRows(new PositiveInteger(rows), 0);
      meta.setPlateColumns(new PositiveInteger(cols), 0);
      meta.setPlateName("First test Plate", 0);
      
      PositiveInteger pwidth = new PositiveInteger(width);
      PositiveInteger pheight = new PositiveInteger(height);
      
      char rowChar = 'A';
      for (int row = 0; row  < rows; row++) {
        for (int column = 0; column < cols; column++) {
          
          // set up well
          String wellID = MetadataTools.createLSID("Well:", well);
          meta.setWellID(wellID, plateIndex, well);
          meta.setWellRow(new NonNegativeInteger(row), plateIndex, well);
          meta.setWellColumn(new NonNegativeInteger(column), plateIndex, well); 
          
          int nFOV= nFovs[row][column];
          
          for(int fov = 0; fov < nFOV ; fov++)  {
         
            // Create Image NB numberng in the Name goes from 1->n not 0-> n-1
            String imageName = rowChar + ":" + Integer.toString(column + 1) + ":FOV:" + Integer.toString(fov + 1);
            String imageID = MetadataTools.createLSID("Image", well, fov);
            meta.setImageID(imageID, series);
            meta.setImageName(imageName, series);
            
            String pixelsID = MetadataTools.createLSID("Pixels",row, well, fov);
            meta.setPixelsID(pixelsID, series);
            
            // specify that the pixel data is stored in big-endian format
            // change 'TRUE' to 'FALSE' to specify little-endian format
            meta.setPixelsBinDataBigEndian(Boolean.TRUE,  series, 0);

            // specify that the image is stored in ZCT order
            meta.setPixelsDimensionOrder(DimensionOrder.XYZCT, series);

            // specify the pixel type of the image
            meta.setPixelsType(PixelType.fromString(FormatTools.getPixelTypeString(pixelType)), series);

            // specify the dimensions of the image
            meta.setPixelsSizeX(pwidth, series);
            meta.setPixelsSizeY(pheight, series);
            meta.setPixelsSizeZ(new PositiveInteger(1), series);
            meta.setPixelsSizeC(new PositiveInteger(1), series);
            meta.setPixelsSizeT(new PositiveInteger(sizet), series);

            // define each channel and specify the number of samples in the channel
            // the number of samples is 3 for RGB images and 1 otherwise
            String channelID = MetadataTools.createLSID("Channel",well, fov);
            meta.setChannelID(channelID, series,0 );
            meta.setChannelSamplesPerPixel(new PositiveInteger(1), series, 0);
           
            // set sample
            String wellSampleID = MetadataTools.createLSID("WellSample",well, fov);
            meta.setWellSampleID(wellSampleID,0,well,fov);
            // NB sampleIndex here == series ie the image No
            meta.setWellSampleIndex(new NonNegativeInteger(series), 0, well, fov);
            meta.setWellSampleImageRef(imageID, 0, well, fov);
            
            if (exposureTimes != null && exposureTimes.length == sizet)  {
              for (int t = 0; t < sizet; t++)  {
                meta.setPlaneTheT(new NonNegativeInteger(t), series, t);
                meta.setPlaneTheC(new NonNegativeInteger(0), series, t);
                meta.setPlaneTheZ(new NonNegativeInteger(0), series, t);
                meta.setPlaneExposureTime(exposureTimes[t], series, t);
              } 
            }
            
            // add FLIM ModuloAlongT annotation if required 
            if (delays != null)  {
              CoreMetadata modlo = createModuloAnn(meta);
              service.addModuloAlong(meta, modlo, series);
            }
            
            series++;
          }  //end of samples  
          well++;
        }
        rowChar++;
      }
      
      //String dump = meta.dumpXML();
      //System.out.println("dump = ");
      //System.out.println(dump);
      return meta;
    }
    
    catch (DependencyException e) {
      exception = e;
    }
    catch (ServiceException e) {
      exception = e;
    }
    catch (EnumerationException e) {
      exception = e;
    }

    System.err.println("Failed to populate OME-XML metadata object.");
    exception.printStackTrace();
    return null;    
      
  }
  
  
  /**
   * Setup delays.
   */
  private boolean setupModulo(ArrayList<String> delays) {
    
    boolean success = false;
    if (delays.size() == sizet)  {
      this.delays = delays;
      success = true;
    }
    return success;
  
  }

  
   /**
   * Add ModuloAlong annotation.
   */
  private CoreMetadata createModuloAnn(IMetadata meta) {

    CoreMetadata modlo = new CoreMetadata();

    modlo.moduloT.type = loci.formats.FormatTools.LIFETIME;
    modlo.moduloT.unit = "ps";
    modlo.moduloT.typeDescription = "Gated";

    modlo.moduloT.labels = new String[sizet];

    for (int i = 0; i < sizet; i++) {
      //System.out.println(delays.get(i));
      modlo.moduloT.labels[i] = delays.get(i);
      
    }

    return modlo;
  }

  /**
   * Save a plane of pixel data to the output file.
   *
   * @param width the width of the image in pixels
   * @param height the height of the image in pixels
   * @param pixelType the pixel type of the image; @see loci.formats.FormatTools
   */
  private void savePlane(byte[] plane, int index) {
    
    Exception exception = null;
    try {
      writer.saveBytes(index, plane);
    }
    catch (FormatException e) {
      exception = e;
    }
    catch (IOException e) {
      exception = e;
    }
    if (exception != null) {
      System.err.println("Failed to save plane.");
      exception.printStackTrace();
    }
  }

  
  /** Close the file writer. */
  public void cleanup() {
    if (writer != null)  {
      try {
        writer.close();
      }
      catch (IOException e) {
        System.err.println("Failed to close file writer.");
      }
    }
  }
  
}
