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

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
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

import ome.xml.model.enums.DimensionOrder;
import ome.xml.model.enums.EnumerationException;
import ome.xml.model.enums.PixelType;
import ome.xml.model.primitives.PositiveInteger;
import ome.xml.model.primitives.NonNegativeInteger;
import ome.xml.model.enums.NamingConvention;

/**
 * Example class that shows how to export raw pixel data to OME-TIFF as a Plate using
 * Bio-Formats version 4.2 or later.
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
  
  /** The file writer. */
  private ImageWriter writer;

  /** The name of the current output file. */
  private final String outputFile;
  

  /**
   * Construct a new FileWriteSPW that will save to the specified file.
   *
   * @param outputFile the file to which we will export
   */
  public FileWriteSPW(String outputFile) {
    this.outputFile = outputFile;       
  }
  
  public boolean init( int[][] nFov, int sizeX, int  sizeY, int sizet)  {
    this.rows = nFov.length;
    this.cols = nFov[0].length;
    width = sizeX;
    this.height = sizeY;
    this.sizet = sizet;
    IMetadata omexml = initializeMetadata(nFov);
     
    Path path = FileSystems.getDefault().getPath(outputFile);
      //delete if exists 
    //NB deleting old files seems to be critical when changing size
    try {
      boolean success = Files.deleteIfExists(path);
      System.out.println("Delete status: " + success);
    } catch (IOException | SecurityException e) {
      System.err.println(e);
    }
    
    initializationSuccess = initializeWriter(omexml);
    
    return initializationSuccess;
  }

  /** Save a single  uint16 plane of data.
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
      
      String suffixStr;
      int plateIndex = 0;
      int series = 0;
      int well = 0;
      int nFov;
      String wellSampleID;
      
      // Create Minimal 2x2 Plate 
      meta.setPlateID(MetadataTools.createLSID("Plate", 0), 0);
   
      meta.setPlateRowNamingConvention(NamingConvention.LETTER, 0);
      meta.setPlateColumnNamingConvention(NamingConvention.NUMBER, 0);

      meta.setPlateRows(new PositiveInteger(rows), 0);
      meta.setPlateColumns(new PositiveInteger(cols), 0);
      meta.setPlateName("First test Plate", 0);
      PositiveInteger pwidth = new PositiveInteger(width);
      PositiveInteger pheight = new PositiveInteger(height);
      
      for (int row = 0; row  < rows; row++) {
        for (int column = 0; column < cols; column++) {
          
          suffixStr =  String.valueOf((char)(row + 65)) + ":" + Integer.toString(column + 1);
          
          // set up well
          String wellID = MetadataTools.createLSID("Well:" + suffixStr, 0, well);
          meta.setWellID(wellID, plateIndex, well);
          meta.setWellRow(new NonNegativeInteger(row), plateIndex, well);
          meta.setWellColumn(new NonNegativeInteger(column), plateIndex, well);
          
          nFov = nFovs[row][column];
          
          if (nFov > 0)  {
            
            for (int sampleIndex = 0; sampleIndex < nFov; sampleIndex++) {
          
              // Create Image
              String sampleStr = Integer.toString(sampleIndex);
              String imageID = MetadataTools.createLSID("Image:" + suffixStr + ":FOV:" + sampleStr, series);
              meta.setImageID(imageID, series);
              meta.setImageName("Image: " + suffixStr, series);
              meta.setPixelsID("Pixels:0:" + suffixStr, series);

              // specify that the pixel data is stored in big-endian format
              // change 'TRUE' to 'FALSE' to specify little-endian format
              meta.setPixelsBinDataBigEndian(Boolean.TRUE,  series, 0);

              // specify that the images are stored in ZCT order
              meta.setPixelsDimensionOrder(DimensionOrder.XYZCT, series);

              // specify that the pixel type of the image
              meta.setPixelsType(PixelType.fromString(FormatTools.getPixelTypeString(pixelType)), series);

              // specify the dimensions of the images
              meta.setPixelsSizeX(pwidth, series);
              meta.setPixelsSizeY(pheight, series);
              meta.setPixelsSizeZ(new PositiveInteger(1), series);
              meta.setPixelsSizeC(new PositiveInteger(1), series);
              meta.setPixelsSizeT(new PositiveInteger(sizet), series);

              // define each channel and specify the number of samples in the channel
              // the number of samples is 3 for RGB images and 1 otherwise
              meta.setChannelID("Channel:0:" + suffixStr, series, 0);
              meta.setChannelSamplesPerPixel(new PositiveInteger(1), series, 0);
          
              wellSampleID = MetadataTools.createLSID("WellSample:" + sampleStr, 0, series, sampleIndex);
              System.out.println(suffixStr);
              System.out.println(series);
              System.out.println(sampleIndex);
              
              
              meta.setWellSampleID(wellSampleID, 0, well, sampleIndex);
              meta.setWellSampleIndex(new NonNegativeInteger(series), 0, series, sampleIndex);
              meta.setWellSampleImageRef(imageID, 0, series, sampleIndex);

              // add FLIM ModuloAlongT annotation if required 
              if (delays != null) {
                CoreMetadata modlo = createModuloAnn(meta);
                service.addModuloAlong(meta, modlo, series);
              }
            
              series++;
            
            }
          }

          well++;
      
        }
      }
      
      //String dump = meta.dumpXML();
      //System.out.println("dump = ");
      //System.out.println(dump);
      return meta;
      }
    
    catch (ServiceException | EnumerationException | DependencyException e) {
      exception = e;
    }
    
    System.err.println("Failed to populate OME-XML metadata object.");
    return null;
      
  }
  
  
  /**
   * Setup delays.
   */
  public boolean setupModulo(ArrayList<String> delays) {
    
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
    catch (FormatException | IOException e) {
      exception = e;
    }
    if (exception != null) {
      System.err.println("Failed to save plane.");
    }
  }

  

  /** Close the file writer. */
  public void cleanup() {
    try {
      writer.close();
    }
    catch (IOException e) {
      System.err.println("Failed to close file writer.");
    }
  }
  
}
