/**
 *  neuroConstruct
 *  Software for developing large scale 3D networks of biologically realistic neurons
 *
 *  Copyright (c) 2009 Padraig Gleeson
 *  UCL Department of Neuroscience, Physiology and Pharmacology
 *
 *  Development of this software was made possible with funding from the
 *  Medical Research Council and the Wellcome Trust
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.

 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 */

package ucl.physiol.neuroconstruct.neuroml;

import java.io.*;
import java.util.*;


import ucl.physiol.neuroconstruct.cell.*;
import ucl.physiol.neuroconstruct.mechanisms.*;
import ucl.physiol.neuroconstruct.cell.compartmentalisation.*;
import ucl.physiol.neuroconstruct.cell.converters.*;
import ucl.physiol.neuroconstruct.neuroml.LemsConstants.LemsOption;
import ucl.physiol.neuroconstruct.neuroml.NeuroMLConstants.*;
import ucl.physiol.neuroconstruct.neuron.NeuronFileManager;
import ucl.physiol.neuroconstruct.project.*;
import ucl.physiol.neuroconstruct.project.GeneratedPlotSaves.PlotSaveDetails;
import ucl.physiol.neuroconstruct.simulation.SimulationData;
import ucl.physiol.neuroconstruct.simulation.SimulationsInfo;
import ucl.physiol.neuroconstruct.utils.*;
import ucl.physiol.neuroconstruct.utils.XMLUtils;
import ucl.physiol.neuroconstruct.utils.units.UnitConverter;
import ucl.physiol.neuroconstruct.utils.units.Units;
import ucl.physiol.neuroconstruct.utils.xml.*;

import org.neuroml.model.util.NeuroMLElements;

/**
 * Main file for generating simulations based on NeuroML
 *
 * @author Padraig Gleeson
 *
 */

public class NeuroMLFileManager
{

    private static ClassLogger logger = new ClassLogger("NeuroMLFileManager");
    Project project = null;
    /////File mainSimFile = null;
    int randomSeed = 0;
    /**
     * The time last taken to generate the main files
     */
    private float genTime = -1;
    boolean mainFileGenerated = false;
    private HashMap<String, Integer> nextColour = new HashMap<String, Integer>();

    private NeuroMLFileManager()
    {
    }

    public NeuroMLFileManager(Project project)
    {
        this.project = project;
    }

    public void reset()
    {
        nextColour = new HashMap<String, Integer>();

    }
    
    public static boolean fileClaimsToBeNeuroML2(File nmlFile) throws NeuroMLException
    {
        try 
        {
            InputStream fis = new FileInputStream(nmlFile);
            BufferedReader br = new BufferedReader(new InputStreamReader(fis));
            for (int i=0;i<5;i++)
            {
                String line = br.readLine();
                if (line.indexOf("neuroml2")>0)
                    return true;
            }
        }
        catch (IOException e) 
        {
            throw new NeuroMLException("Unable to determine version of: "+nmlFile, e);
        }
        return false;
        
    }

    public static File saveNetworkStructureXML(Project project,
                                               File neuroMLFile,
                                               boolean zipped,
                                               boolean extraComments,
                                               String simConfig,
                                               String units,
                                               NeuroMLVersion version) throws NeuroMLException
    {
        return saveNetworkStructureXML(project,
                                        neuroMLFile,
                                        zipped,
                                        extraComments,
                                        simConfig,
                                        units,
                                        version,
                                        null);
    }
    public static File saveNetworkStructureXML(Project project,
                                               File neuroMLFile,
                                               boolean zipped,
                                               boolean extraComments,
                                               String simConfig,
                                               String units,
                                               NeuroMLVersion version,
                                               ArrayList<File> filesToInclude) throws NeuroMLException
    {
        int preferredUnits = UnitConverter.getUnitSystemIndex(units);

        try
        {

            StringBuilder notes = new StringBuilder("\nNetwork structure (NeuroML " + version + ") for project: "
                                                    + project.getProjectName() + " saved with neuroConstruct v"
                                                    + GeneralProperties.getVersionNumber() + " on: " + GeneralUtils.getCurrentTimeAsNiceString() + ", "
                                                    + GeneralUtils.getCurrentDateAsNiceString() + "\n\n");


            Iterator<String> cellGroups = project.generatedCellPositions.getNamesGeneratedCellGroups();

            while (cellGroups.hasNext())
            {
                String cg = cellGroups.next();
                int numHere = project.generatedCellPositions.getNumberInCellGroup(cg);
                if (numHere > 0)
                {
                    notes.append("Cell Group: " + cg + " contains " + numHere + " cells\n");
                }

            }
            notes.append("\n");

            Iterator<String> netConns = project.generatedNetworkConnections.getNamesNetConnsIter();

            while (netConns.hasNext())
            {
                String mc = netConns.next();
                int numHere = project.generatedNetworkConnections.getSynapticConnections(mc).size();
                if (numHere > 0)
                {
                    notes.append("Network connection: " + mc + " contains " + numHere + " individual synaptic connections\n");
                }

            }
            notes.append("\n");

            logger.logComment("Going to save network in NeuroML format in " + neuroMLFile.getAbsolutePath());

            SimpleXMLDocument doc = new SimpleXMLDocument();

            SimpleXMLElement rootElement = null;

            boolean nml2 = version.isVersion2();

            String rootName = NetworkMLConstants.ROOT_ELEMENT;
            String defNamespace = NetworkMLConstants.NAMESPACE_URI;
            String loc = NetworkMLConstants.DEFAULT_SCHEMA_FILENAME;

            String metaPrefix = MetadataConstants.PREFIX + ":";

            if (nml2)
            {
                rootName = NeuroMLConstants.ROOT_ELEMENT;
                defNamespace = NeuroMLConstants.NAMESPACE_URI_VERSION_2;
                if (version.isVersion2alpha())
                {
                    loc = NeuroMLElements.DEFAULT_SCHEMA_LOCATION_VERSION_2_ALPHA;
                }
                else
                {
                    loc = NeuroMLElements.DEFAULT_SCHEMA_LOCATION_VERSION_2_BETA1;
                }
                metaPrefix = "";
            }


            rootElement = new SimpleXMLElement(rootName);

            rootElement.addNamespace(new SimpleXMLNamespace("", defNamespace));

            if (!nml2)
            {
                rootElement.addNamespace(new SimpleXMLNamespace(MetadataConstants.PREFIX,
                                                                MetadataConstants.NAMESPACE_URI));
            }

            rootElement.addNamespace(new SimpleXMLNamespace(NeuroMLConstants.XSI_PREFIX,
                                                            NeuroMLConstants.XSI_URI));

            rootElement.addAttribute(new SimpleXMLAttribute(NeuroMLConstants.XSI_SCHEMA_LOC,
                                                            defNamespace
                                                            + "  " + loc));

            if (!nml2)
            {
                rootElement.addAttribute(new SimpleXMLAttribute(MetadataConstants.LENGTH_UNITS_OLD, MetadataConstants.LENGTH_UNITS_MICROMETER));
            }
            else
            {
                rootElement.addAttribute(NeuroMLConstants.NEUROML_ID_V2, "network_" + project.getProjectName());
            }

            doc.addRootElement(rootElement);

            logger.logComment("    ****    Full XML:  ****");
            logger.logComment("  ");



            rootElement.addContent("\n\n    ");

            rootElement.addChildElement(new SimpleXMLElement(metaPrefix
                                                             + MetadataConstants.NOTES_ELEMENT, "\n" + notes.toString()+"\n    "));

            SimpleXMLElement props = new SimpleXMLElement(metaPrefix + MorphMLConstants.PROPS_ELEMENT);

            rootElement.addContent("\n\n");

            if (!nml2)
            {
                rootElement.addChildElement(props);
            }

            MetadataConstants.addProperty(props,
                                          NetworkMLConstants.NC_NETWORK_GEN_RAND_SEED,
                                          project.generatedCellPositions.getRandomSeed() + "",
                                          "    ",
                                          version);

            if (simConfig != null)
            {
                MetadataConstants.addProperty(props,
                                              NetworkMLConstants.NC_SIM_CONFIG,
                                              simConfig,
                                              "    ",
                                              version);
            }

            SimConfig sc = project.simConfigInfo.getSimConfig(simConfig);

            MetadataConstants.addProperty(props,
                                          NetworkMLConstants.NC_SIM_DURATION,
                                          Float.toString(sc.getSimDuration()),
                                          "    ");

            MetadataConstants.addProperty(props,
                                          NetworkMLConstants.NC_SIM_TIME_STEP,
                                          Float.toString(project.simulationParameters.getDt()),
                                          "    ");

            MetadataConstants.addProperty(props,
                                          NetworkMLConstants.NC_TEMPERATURE,
                                          Float.toString(project.simulationParameters.getTemperature()),
                                          "    ");

            //SimpleXMLElement popRoot = rootElement;

            SimpleXMLElement topLevelCompElement = null;

            if (nml2)
            {
                    
                if (filesToInclude!=null) 
                {
                    filesToInclude = (ArrayList<File>)GeneralUtils.reorderAlphabetically(filesToInclude, true);
                    for (File f: filesToInclude) 
                    {
                        SimpleXMLElement incEl1 = new SimpleXMLElement(MorphMLConstants.INCLUDE_V2);
                        incEl1.addAttribute(MorphMLConstants.HREF_V2, f.getName());
                        
                        rootElement.addContent("\n    ");
                        rootElement.addChildElement(incEl1);
                        
                    }
                }
                
                SimpleXMLElement networkNml2 = new SimpleXMLElement(NetworkMLConstants.NEUROML2_NETWORK_ELEMENT);
                networkNml2.addAttribute(NeuroMLConstants.NEUROML_ID_V2, NetworkMLConstants.NEUROML2_NETWORK_ID_PREFIX + project.getProjectName());

                topLevelCompElement = rootElement;

                rootElement = networkNml2;

                if (version.isVersion2alpha())
                {
                    SimpleXMLElement excellPropsElement = new SimpleXMLElement(NetworkMLConstants.NEUROML2_ALPHA_EXTRACELLULAR_PROPS_ELEMENT);
                    excellPropsElement.addAttribute(NetworkMLConstants.NEUROML2_TEMPERATURE_ATTR, project.simulationParameters.getTemperature() + " degC");
                    networkNml2.addContent("\n\n        ");
                    networkNml2.addChildElement(excellPropsElement);
                }
                else
                {
                    networkNml2.addAttribute(NeuroMLConstants.NEUROML_COMP_TYPE_ATTR, NetworkMLConstants.NEUROML2_NETWORK_WITH_TEMP_TYPE);
                    networkNml2.addAttribute(NetworkMLConstants.NEUROML2_TEMPERATURE_ATTR, project.simulationParameters.getTemperature() + " degC");
                    networkNml2.addContent("\n\n        ");
                }
            }



            ArrayList<SimpleXMLElement> popElements = project.generatedCellPositions.getNetworkMLElements(version);

            for (SimpleXMLElement popEl : popElements)
            {
                rootElement.addContent("\n\n        ");

                rootElement.addChildElement(popEl);

            }

            rootElement.addContent("\n\n");

            ArrayList<SimpleXMLEntity> netEntities = project.generatedNetworkConnections.getNeuroMLElements(preferredUnits, extraComments, version);

            for (SimpleXMLEntity netEntity : netEntities)
            {
                rootElement.addContent("\n\n        ");
                if (netEntity instanceof SimpleXMLElement)
                {
                    rootElement.addChildElement((SimpleXMLElement) netEntity);
                }
                else if (netEntity instanceof SimpleXMLComment)
                {
                    rootElement.addComment((SimpleXMLComment) netEntity);
                }
            }


            ArrayList<SimpleXMLEntity> elecInputEntities = project.generatedElecInputs.getNetworkMLEntities(preferredUnits, version, topLevelCompElement);

            for (SimpleXMLEntity elecInputEntity : elecInputEntities)
            {
                int endPops = -1;
                int endProjs = -1;
                boolean foundPop = false;
                boolean foundProj = false;
                ArrayList<SimpleXMLEntity> ents = rootElement.getContents();
                for (int i=0;i<ents.size();i++){
                    SimpleXMLEntity sxe = ents.get(i);
                
                    boolean isElement = sxe instanceof SimpleXMLElement;
                    if (isElement)
                    {
                        SimpleXMLElement sxel = (SimpleXMLElement)sxe;
                        
                        //System.out.println("sxel: "+sxel+", i "+i+", foundPop"+foundPop);
                        if (!foundPop && sxel.getName().equals(NetworkMLConstants.POPULATION_ELEMENT))
                        {
                            foundPop = true;
                        }
                        if (foundPop && !sxel.getName().equals(NetworkMLConstants.POPULATION_ELEMENT))
                        {
                            foundPop = false;
                            endPops = i;
                        }
                        if (!foundProj && sxel.getName().equals(NetworkMLConstants.PROJECTION_ELEMENT))
                        {
                            foundProj = true;
                        }
                        if (foundProj && !sxel.getName().equals(NetworkMLConstants.PROJECTIONS_ELEMENT))
                        {
                            foundProj = false;
                            endProjs = i;
                        }
                    }
                        
                }
                if (endPops<0)
                    endPops = ents.size();
                if (endProjs<0)
                    endProjs = endPops;
                
                rootElement.addContent("\n\n        ");
                if (elecInputEntity instanceof SimpleXMLElement)
                {
                    SimpleXMLElement sxe = (SimpleXMLElement) elecInputEntity;
                    
                    if (sxe.getName().equals(NetworkMLConstants.POPULATION_ELEMENT)) 
                    {
                        rootElement.addChildElementAt(sxe, endPops);
                        endPops = endPops+1;
                    }
                    else if (sxe.getName().equals(NetworkMLConstants.PROJECTION_ELEMENT)) 
                    {
                        rootElement.addChildElementAt(sxe, endProjs);
                        endProjs = endProjs +1;
                    }
                    else 
                    {
                        rootElement.addChildElement(sxe);
                    }
                    rootElement.addContent("\n\n");
                }
                else if (elecInputEntity instanceof SimpleXMLComment)
                {
                    rootElement.addComment((SimpleXMLComment) elecInputEntity);
                }
            }
            rootElement.addContent("\n\n");


            if (nml2)
            {
                topLevelCompElement.addContent("\n    ");
                topLevelCompElement.addChildElement(rootElement);
                topLevelCompElement.addContent("\n\n");
            }

            rootElement.addContent("\n\n");
            if (nml2)
            {
                rootElement.addContent("    ");
            }

            String stringForm = doc.getXMLString("", false);

            logger.logComment(stringForm);

            if (!zipped)
            {
                FileWriter fw = new FileWriter(neuroMLFile);
                fw.write(stringForm);
                fw.close();

                return neuroMLFile;
            }
            else
            {
                File zipFile = neuroMLFile;

                if (!neuroMLFile.getName().endsWith(ProjectStructure.getNeuroMLCompressedFileExtension()))
                {
                    zipFile = new File(neuroMLFile.getAbsolutePath()
                                       + ProjectStructure.getNeuroMLCompressedFileExtension());
                }

                String extension = version.isVersion2() ? ProjectStructure.getNeuroML2FileExtension() : ProjectStructure.getNeuroML1FileExtension();
                
                String internalFilename = GeneralUtils.replaceAllTokens(zipFile.getName(),
                                                                        ProjectStructure.getNeuroMLCompressedFileExtension(),
                                                                        extension);

                ZipUtils.zipStringAsFile(stringForm, zipFile, internalFilename, notes.toString());

                return zipFile;
            }
        }
        catch (NeuroMLException ex)
        {
            logger.logError("Problem creating NeuroML file: " + neuroMLFile.getAbsolutePath(), ex);

            throw new NeuroMLException("Problem creating NeuroML file: " + neuroMLFile.getAbsolutePath() + "\n" + ex.getMessage(), ex);
        } catch (IOException ex) {
            logger.logError("Problem creating NeuroML file: " + neuroMLFile.getAbsolutePath(), ex);
            
            throw new NeuroMLException("Problem creating NeuroML file: " + neuroMLFile.getAbsolutePath() + "\n" + ex.getMessage(), ex);
        }
    }

    public void generateNeuroMLFiles(SimConfig simConf,
                                     MorphCompartmentalisation mc,
                                     int seed,
                                     boolean singleL3File) throws IOException
    {
        generateNeuroMLFiles(simConf, mc, seed, singleL3File, false);
    }

    public void generateNeuroMLFiles(SimConfig simConf,
                                     MorphCompartmentalisation mc,
                                     int seed,
                                     boolean singleL3File,
                                     boolean annotations) throws IOException
    {
        generateNeuroMLFiles(simConf, NeuroMLVersion.NEUROML_VERSION_1, LemsOption.NONE, mc, seed, singleL3File, annotations);
    }

    public void generateNeuroMLFiles(SimConfig simConf,
                                     NeuroMLVersion version,
                                     LemsOption lemsOption,
                                     MorphCompartmentalisation mc,
                                     int seed,
                                     boolean singleL3File,
                                     boolean annotations) throws IOException
    {
        generateNeuroMLFiles(simConf, version, lemsOption, mc, seed, singleL3File, annotations, false);
    }

    public void generateNeuroMLFiles(SimConfig simConf,
                                     NeuroMLVersion version,
                                     LemsOption lemsOption,
                                     MorphCompartmentalisation mc,
                                     int seed,
                                     boolean singleL3File,
                                     boolean annotations,
                                     boolean runInBackground) throws IOException
    {

        File neuroMLDir = ProjectStructure.getNeuroMLDir(project.getProjectMainDirectory(), version);

        generateNeuroMLFiles(simConf,
                             version,
                             lemsOption,
                             mc,
                             seed,
                             singleL3File,
                             annotations,
                             neuroMLDir,
                             runInBackground);
    }

    public void generateNeuroMLFiles(SimConfig simConf,
                                     NeuroMLVersion version,
                                     LemsOption lemsOption,
                                     MorphCompartmentalisation mc,
                                     int seed,
                                     boolean singleL3File,
                                     boolean annotations,
                                     File generateDir,
                                     boolean runInBackground) throws IOException
    {
        generateNeuroMLFiles(simConf,
                             version,
                             lemsOption,
                             mc,
                             seed,
                             singleL3File,
                             annotations,
                             generateDir,
                             UnitConverter.getUnitSystemDescription(UnitConverter.GENESIS_SI_UNITS),
                             runInBackground);
    }

    public void generateNeuroMLFiles(SimConfig simConf,
                                     NeuroMLVersion version,
                                     LemsOption lemsOption,
                                     MorphCompartmentalisation mc,
                                     int seed,
                                     boolean singleL3File,
                                     boolean annotations,
                                     File generateDir,
                                     String units,
                                     boolean runInBackground) throws IOException
    {
        logger.logComment("Starting generation of the files into dir: " + generateDir.getCanonicalPath() + ", version: " + version, true);
        
        if (version.isVersion2alpha()){
            logger.logComment("Generated units for LEMS/NML 2 alpha must be Physiological units... " , true);
            units = UnitConverter.getUnitSystemDescription(UnitConverter.GENESIS_PHYSIOLOGICAL_UNITS);
        }

        int preferredUnits = UnitConverter.getUnitSystemIndex(units);

        GeneralUtils.removeAllFiles(generateDir, false, false, true);

        if (!generateDir.exists())
        {
            generateDir.mkdir();
        }


        randomSeed = seed;

        ArrayList<File> generatedChanSynFiles = new ArrayList<File>();
        ArrayList<File> generatedCellFiles = new ArrayList<File>();
        File generatedNetFile = null;


        String timeInfo = GeneralUtils.getCurrentDateAsNiceString() + "_" + GeneralUtils.getCurrentTimeAsNiceString();

        timeInfo = GeneralUtils.replaceAllTokens(timeInfo, ":", "-");
        
        String lastExtension = ProjectStructure.getNeuroML1FileExtension();

        String fileName = "L3Net_" + timeInfo + ProjectStructure.getNeuroML1FileExtension();

        if (version.isVersion2())
        {
            lastExtension = ProjectStructure.getNeuroML2FileExtension();
            fileName = project.getProjectName() + ProjectStructure.neuroml2NetworkExtension + lastExtension;
        }

        File fileToGen = new File(generateDir, fileName);

        boolean pynnCellsPresent = true;

        if (singleL3File)
        {
            File netFile = null;
            if (annotations)
            {
                try
                {
                    netFile = ProjectManager.saveLevel3NetworkXML(project,
                                                                  fileToGen,
                                                                  false, false, annotations,
                                                                  simConf.getName(),
                                                                  units);
                }
                catch (NeuroMLException ex)
                {
                    GuiUtils.showErrorMessage(logger, "Problem saving complete network with annotations in NeuroML", ex, null);
                }
            }
            else
            {
                try
                {
                    netFile = ProjectManager.saveLevel3NetworkXML(project,
                                                                  fileToGen,
                                                                  false, false,
                                                                  simConf.getName(),
                                                                  units);
                }
                catch (NeuroMLException ex)
                {
                    GuiUtils.showErrorMessage(logger, "Problem saving complete network in NeuroML:\n"
                                                      + "" + ex.getMessage(), ex, null);
                }
            }
            generatedNetFile = netFile;

        }
        else
        {

            ArrayList<Cell> generatedCells = null;

            if (project.generatedCellPositions.getAllPositionRecords().isEmpty())
            {
                simConf = null; // signifies no particular sim conf, so gen all cells, channels, etc.
            }
            try
            {
                NeuroMLLevel level = NeuroMLLevel.NEUROML_LEVEL_3;
                
                String cellExtension = lastExtension;

                if (version.isVersion2())
                {
                    level = NeuroMLLevel.NEUROML_VERSION_2_SPIKING_CELL;
                    cellExtension = ProjectStructure.neuroml2CellExtension+ ProjectStructure.getNeuroML2FileExtension();
                }

                generatedCells = MorphMLConverter.saveAllCellsInNeuroML(project,
                                                                        mc,
                                                                        level,
                                                                        version,
                                                                        simConf,
                                                                        generateDir);

                for (Cell cell : generatedCells)
                {
                    generatedCellFiles.add(new File(generateDir, cell.getInstanceName() + cellExtension));
                }
            }
            catch (MorphologyException ex1)
            {
                GuiUtils.showErrorMessage(logger, "Problem saving cells in NeuroML format", ex1, null);
                return;
            }

            ArrayList<String> cellMechFilesHandled = new ArrayList<String>();
            ArrayList<String> synsToInc = new ArrayList<String>();

            for (String netConn : project.generatedNetworkConnections.getNamesNonEmptyNetConns())
            {
                for (SynapticProperties sp : project.morphNetworkConnectionsInfo.getSynapseList(netConn))
                {
                    synsToInc.add(sp.getSynapseType());

                }
            }


            for (Cell nextCell : generatedCells)
            {
                ArrayList<String> cellMechs = new ArrayList<String>();
                ArrayList<String> chanMechNames = nextCell.getAllChanMechNames(true);

                for (String cm : chanMechNames)
                {
                    cellMechs.add(cm);
                }
                ArrayList<String> syns = nextCell.getAllAllowedSynapseTypes();

                for (String syn : syns)
                {
                    if (synsToInc.contains(syn))
                    {
                        cellMechs.add(syn);
                    }
                }

                logger.logComment("cellMechs: " + cellMechs);

                for (String cellMech : cellMechs)
                {
                    String mechExtension = lastExtension;
                    
                    if (!cellMechFilesHandled.contains(cellMech))
                    {
                        CellMechanism cm = project.cellMechanismInfo.getCellMechanism(cellMech);

                        if (cm == null)
                        {
                            //??
                        }
                        else if (cm instanceof NeuroML2Component)
                        {
                            NeuroML2Component nmlCm = (NeuroML2Component) cm;
                            
                            if (!cm.isMechanismForNeuroML2Cell()) // NML2 file will be generated/added when generating nml2 for cell if it's a MechanismForNeuroML2Cell
                            {
                                String extraExtn = "";
                                if (nmlCm.isChannelMechanism()) 
                                    extraExtn = ProjectStructure.neuroml2ChannelExtension;
                                else if (nmlCm.isSynapticMechanism()) 
                                    extraExtn = ProjectStructure.neuroml2SynapseExtension;
                                mechExtension = extraExtn+ ProjectStructure.neuroml2Extension;
                                String newName = cm.getInstanceName() + mechExtension;
                                File copied = GeneralUtils.copyFileIntoDir(nmlCm.getXMLFile(project), generateDir);
                                File newFile = new File(generateDir, newName);
                                copied.renameTo(newFile);

                                generatedChanSynFiles.add(newFile);
                                
                                logger.logComment("copied: " + copied, false);
                            }
                            
                            /*
                             if (!pynnCellsPresent) {
                             Sim sim = new Sim(nmlCm.getXMLFile(project));

                             try
                             {
                             sim.readModel();
                             Lems lems = sim.getLems();
                             Component comp = lems.getComponent(cm.getInstanceName());
                             if (comp.getComponentType().isOrExtends("pyNNCell"))
                             pynnCellsPresent = true;
                             }
                             catch (ContentError ce)
                             {
                             throw new IOException("Problem reading LEMS description...", ce);
                             // Ignore, assume it's not a valid LEMS file...
                             }
                             }*/


                        }
                        else if (!(cm instanceof ChannelMLCellMechanism))
                        {
                            File warnFile = new File(generateDir, cm.getInstanceName() + ".warning");
                            try
                            {
                                FileWriter fw = new FileWriter(warnFile);
                                fw.write("Warning: cell mechanism " + cm.getInstanceName() + " is not implemented in ChannelML in the project: " + project.getProjectFileName() + ", and so cannot be used in the Python/NeuroML test simulation.");

                                generatedChanSynFiles.add(warnFile);
                                fw.close();

                            }
                            catch (IOException ioe)
                            {
                                GuiUtils.showErrorMessage(logger, "Problem writing to file: " + warnFile, ioe, null);
                            }
                        }
                        else
                        {
                            ChannelMLCellMechanism cmlCm = (ChannelMLCellMechanism) cm;

                            File origCmlFile = cmlCm.getXMLFile(project);

                            File newCmlFile = new File(generateDir, cm.getInstanceName() + ".xml");

                            if (cmlCm.getMechanismModel().indexOf("ChannelML") >= 0 && version.isVersion2())
                            {
                                String extraExtn = "";
                                if (cmlCm.isChannelMechanism()) 
                                    extraExtn = ProjectStructure.neuroml2ChannelExtension;
                                else if (cmlCm.isSynapticMechanism()) 
                                    extraExtn = ProjectStructure.neuroml2SynapseExtension;
                            
                                newCmlFile = new File(generateDir, cm.getInstanceName() + extraExtn + ".nml");

                                File xslChannelML2NeuroML2 = null;

                                if (version.isVersion2alpha())
                                {
                                    xslChannelML2NeuroML2 = ProjectStructure.getChannelML2NeuroML2alpha();
                                }
                                else if (version.isVersion2Latest())
                                {
                                    xslChannelML2NeuroML2 = ProjectStructure.getChannelML2NeuroML2beta();
                                }


                                String xslContents = GeneralUtils.readShortFile(xslChannelML2NeuroML2);

                                //DONE Make celsius a global variable!!
                                /*String defaultTemp = project.simulationParameters.getTemperature() +" degC";
                                 int start = xslContents.indexOf("<xsl:variable name=\"defaultTemp\">")+33;
                                 int end = xslContents.indexOf("</xsl:variable>", start);
                                 xslContents = xslContents.substring(0, start)+defaultTemp+xslContents.substring(end);*/
                                //System.out.println(xslContents);

                                String nml2Contents = XMLUtils.transform(origCmlFile, xslContents);

                                GeneralUtils.writeShortFile(newCmlFile, nml2Contents);
                                generatedChanSynFiles.add(newCmlFile);

                                //XMLUtils.transform(origCmlFile, xslChannelML2NeuroML2, generateDir, ".nml");

                                //String origFileName = origCmlFile.getName();

                                //File generatedFile = new File(generateDir, origFileName.substring(0, origFileName.length()-4)+".nml");


                                //generatedFile.renameTo(newCmlFile);
                            }
                            else
                            {
                                try
                                {
                                    File copied = GeneralUtils.copyFileIntoDir(origCmlFile, generateDir);

                                    copied.renameTo(newCmlFile);
                                    generatedChanSynFiles.add(newCmlFile);

                                }
                                catch (IOException ioe)
                                {
                                    GuiUtils.showErrorMessage(logger, "Problem writing to file: " + cmlCm, ioe, null);
                                }
                            }
                        }
                        cellMechFilesHandled.add(cellMech);
                    }
                }
            }


            String networkFileName = NetworkMLConstants.DEFAULT_NETWORKML_FILENAME_XML;
            if (version.isVersion2())
            {
                networkFileName = project.getProjectName() + ProjectStructure.neuroml2NetworkExtension+ProjectStructure.neuroml2Extension;
            }
            File networkFile = new File(generateDir, networkFileName);

            if (project.generatedCellPositions.getAllPositionRecords().size() > 0)
            {
                try
                {
                    ArrayList<File> allIncludes = new ArrayList<File>();
                    allIncludes.addAll(generatedCellFiles);
                    allIncludes.addAll(generatedChanSynFiles);
                    File netFile = saveNetworkStructureXML(project,
                                                           networkFile,
                                                           false,
                                                           false,
                                                           simConf.getName(),
                                                           units,
                                                           version,
                                                           allIncludes);

                    generatedNetFile = netFile;
                }
                catch (NeuroMLException ex1)
                {
                    GuiUtils.showErrorMessage(logger, "Problem saving network in NeuroML", ex1, null);
                }
            }

        }


        if (lemsOption.doSomething() && version.isVersion2())
        {
            String lemsFileName = "LEMS_" + project.getProjectName() + ".xml";
            File lemsFile = new File(generateDir, lemsFileName);

            SimpleXMLElement lemsElement = new SimpleXMLElement(LemsConstants.ROOT_LEMS);
            
            lemsElement.addNamespace(new SimpleXMLNamespace("", LemsConstants.NAMESPACE_URI));
            
            lemsElement.addNamespace(new SimpleXMLNamespace(NeuroMLConstants.XSI_PREFIX,
                                                                NeuroMLConstants.XSI_URI));
            
            lemsElement.addAttribute(new SimpleXMLAttribute(NeuroMLConstants.XSI_SCHEMA_LOC,
                                                                    LemsConstants.NAMESPACE_URI
                                                                    + "  " + LemsConstants.DEFAULT_SCHEMA_LOCATION));

            lemsElement.addContent("\n\n    "); // to make it more readable...

            String targetElementName = LemsConstants.TARGET_ELEMENT;
            if (version.isVersion2alpha())
            {
                targetElementName = LemsConstants.DEFAULT_RUN_ELEMENT;
            }

            SimpleXMLElement targetElement = new SimpleXMLElement(targetElementName);
            lemsElement.addChildElement(targetElement);
            lemsElement.addContent("\n\n    "); // to make it more readable...

            targetElement.addAttribute(LemsConstants.COMPONENT_ATTR, LemsConstants.DEFAULT_SIM_ID);


            lemsElement.addComment("Include standard NeuroML 2 ComponentType definitions");
            lemsElement.addContent("\n    "); // to make it more readable...

            String prefix = "";
            if (version.isVersion2alpha())
            {
                prefix = NeuroMLConstants.prefixNeuroML2Types;
            }

            SimpleXMLElement incEl1 = new SimpleXMLElement(LemsConstants.INCLUDE_ELEMENT);
            incEl1.addAttribute(LemsConstants.FILE_ATTR, prefix + NeuroMLConstants.NEUROML2_CORE_TYPES_CELLS_DEF);
            lemsElement.addChildElement(incEl1);
            lemsElement.addContent("\n    "); // to make it more readable...


            SimpleXMLElement incEl2 = new SimpleXMLElement(LemsConstants.INCLUDE_ELEMENT);
            incEl2.addAttribute(LemsConstants.FILE_ATTR, prefix + NeuroMLConstants.NEUROML2_CORE_TYPES_NETWORKS_DEF);
            lemsElement.addChildElement(incEl2);
            lemsElement.addContent("\n    "); // to make it more readable...

            SimpleXMLElement incEl3 = new SimpleXMLElement(LemsConstants.INCLUDE_ELEMENT);
            incEl3.addAttribute(LemsConstants.FILE_ATTR, prefix + NeuroMLConstants.NEUROML2_CORE_TYPES_SIMULATION_DEF);
            lemsElement.addChildElement(incEl3);
            lemsElement.addContent("\n\n    "); // to make it more readable...

            if (pynnCellsPresent)
            {
                SimpleXMLElement incEl4 = new SimpleXMLElement(LemsConstants.INCLUDE_ELEMENT);
                incEl4.addAttribute(LemsConstants.FILE_ATTR, prefix + NeuroMLConstants.NEUROML2_CORE_TYPES_PYNN_DEF);
                lemsElement.addChildElement(incEl4);
                lemsElement.addContent("\n\n    "); // to make it more readable...
            }




            lemsElement.addContent("\n\n    "); // to make it more readable...

            lemsElement.addComment("Include the generated NeuroML 2 files");
            lemsElement.addContent("\n\n    "); // to make it more readable...
            
            
            lemsElement.addComment("   Channel/synapse files");
            
            generatedChanSynFiles = (ArrayList<File>)GeneralUtils.reorderAlphabetically(generatedChanSynFiles, true);

            for (File genFile : generatedChanSynFiles)
            {

                SimpleXMLElement incElc = new SimpleXMLElement(LemsConstants.INCLUDE_ELEMENT);
                incElc.addAttribute(LemsConstants.FILE_ATTR, genFile.getName());
                lemsElement.addChildElement(incElc);
                lemsElement.addContent("\n    "); // to make it more readable...
            }
            
            lemsElement.addComment("   Cell files");
            
            generatedCellFiles = (ArrayList<File>)GeneralUtils.reorderAlphabetically(generatedCellFiles, true);

            for (File genFile : generatedCellFiles)
            {

                SimpleXMLElement incElc = new SimpleXMLElement(LemsConstants.INCLUDE_ELEMENT);
                incElc.addAttribute(LemsConstants.FILE_ATTR, genFile.getName());
                lemsElement.addChildElement(incElc);
                lemsElement.addContent("\n    "); // to make it more readable...
            }
            
            lemsElement.addComment("   Network file");
            
            SimpleXMLElement incElc = new SimpleXMLElement(LemsConstants.INCLUDE_ELEMENT);
            incElc.addAttribute(LemsConstants.FILE_ATTR, generatedNetFile.getName());
            lemsElement.addChildElement(incElc);
            lemsElement.addContent("\n    "); // to make it more readable...



            lemsElement.addContent("\n\n    "); // to make it more readable...


            //SimpleXMLElement simEl = new SimpleXMLElement(LemsConstants.SIMULATION_ELEMENT);
   
            lemsElement.addComment("Note: this could be: Simulation id=\""+LemsConstants.DEFAULT_SIM_ID+"\" ... , but Component type=\"Simulation\" ... \n"
                                  + "        is used to allow validation of this file according to the LEMS schema specified above...");
            SimpleXMLElement simEl = new SimpleXMLElement(LemsConstants.COMPONENT_ELEMENT);
            simEl.addAttribute(LemsConstants.COMPONENT_TYPE_ATTR, LemsConstants.SIMULATION_ELEMENT);
            
            lemsElement.addChildElement(simEl);
            simEl.addContent("\n        "); // to make it more readable...


            simEl.addAttribute(NeuroMLConstants.NEUROML_ID_V2, LemsConstants.DEFAULT_SIM_ID);

            simEl.addAttribute(LemsConstants.LENGTH_ATTR, simConf.getSimDuration() + "ms");
            simEl.addAttribute(LemsConstants.STEP_ATTR, project.simulationParameters.getDt() + "ms");
            simEl.addAttribute(LemsConstants.TARGET_ATTR, NetworkMLConstants.NEUROML2_NETWORK_ID_PREFIX + project.getProjectName());




            File dirForAllSims = ProjectStructure.getSimulationsDir(project.getProjectMainDirectory());
            File simDir = new File(dirForAllSims, project.simulationParameters.getReference());

            File summaryFile = new File(simDir, "simulator.props");
            String repFile = summaryFile.getAbsolutePath();
            repFile = repFile.replaceAll("\\\\", "\\\\\\\\");

            File timesFile = new File(simDir, "time.dat");
            String timesFilename = timesFile.getAbsolutePath();
            timesFilename = timesFilename.replaceAll("\\\\", "\\\\\\\\");
            
          
            targetElement.addAttribute(LemsConstants.REPORT_FILE_ATTR, repFile);
            targetElement.addAttribute(LemsConstants.TIMES_FILE_ATTR, timesFilename);


            if (simDir.exists())
            {
                File[] files = simDir.listFiles();
                for (int i = 0; i < files.length; i++)
                {
                    files[i].delete();
                }
                logger.logComment("Directory " + simDir + " being cleansed");
            }
            simDir.mkdir();


            ArrayList<PlotSaveDetails> plots = project.generatedPlotSaves.getPlottedPlotSaves();
            
            HashMap<String, SimpleXMLElement> displaysAdded = new HashMap<String, SimpleXMLElement>();
            ArrayList<SimpleXMLElement> outputFilesAdded = new ArrayList<SimpleXMLElement>();
            
            String timescale;
            float xscale = 1;
            float yscale = 1;

            if (preferredUnits==UnitConverter.GENESIS_PHYSIOLOGICAL_UNITS) 
            {
                timescale = "1ms";
                xscale = 1;
                yscale = 1;
            }
            else
            {
                timescale = "1s";
                xscale = 0.001f;
            }

            

            for (PlotSaveDetails plot : plots)
            {
                ArrayList<Integer> cellNumsToPlot = plot.cellNumsToPlot;

                String displayId = plot.simPlot.getGraphWindow();
                String value = convertValue(plot.simPlot.getValuePlotted());

                logger.logComment("-+- Adding plot: " + plot.simPlot + " " + simConf.toLongString() + " with cells: " + cellNumsToPlot);
                
                Units[] unitsArray = SimPlot.getUnits(plot.simPlot.getValuePlotted());
                if (unitsArray!=null)
                {
                    yscale = (float)UnitConverter.convertFromNeuroConstruct(1, unitsArray[UnitConverter.NEUROCONSTRUCT_UNITS], preferredUnits).getMagnitude();
                } else {
                    yscale = 1;
                }

                if (cellNumsToPlot.size() > 0)
                {
                    if (!displaysAdded.containsKey(displayId))
                    {
                        SimpleXMLElement dispEl = new SimpleXMLElement(LemsConstants.DISPLAY_ELEMENT);
                        

                        simEl.addContent("\n        "); // to make it more readable...
                        simEl.addChildElement(dispEl);
                        simEl.addContent("\n    "); // to make it more readable...

                        dispEl.addAttribute(LemsConstants.ID_ATTR, displayId);
                        dispEl.addAttribute(LemsConstants.TITLE_ATTR, project.getProjectName() + ": " + simConf.getName() + ", " + plot.simPlot.getCellGroup());
                        

                        if (version.isVersion2Latest())
                        {
                            
                            float xmin = 0;
                            float xmax = simConf.getSimDuration();
                            float xdel = (xmax - xmin) * 0.1f;

                            float ymin = plot.simPlot.getMinValue();
                            float ymax = plot.simPlot.getMaxValue();
                            float ydel = (ymax - ymin) * 0.1f;

                            //TODO: scale with correct units!

                            dispEl.addAttribute(LemsConstants.XMIN_ATTR, (xmin - xdel)*xscale + "");
                            dispEl.addAttribute(LemsConstants.XMAX_ATTR, (xmax + xdel)*xscale + "");
                            dispEl.addAttribute(LemsConstants.YMIN_ATTR, (ymin - ydel)*yscale + "");
                            dispEl.addAttribute(LemsConstants.YMAX_ATTR, (ymax + ydel)*yscale + "");
                        }
                        dispEl.addAttribute(LemsConstants.TIMESCALE_ATTR, timescale);

                        displaysAdded.put(displayId, dispEl);
                        
                    }

                    for (int cellNum : cellNumsToPlot)
                    {
                        SimpleXMLElement lineEl = new SimpleXMLElement(LemsConstants.LINE_ELEMENT);
                        SimpleXMLElement dispEl = displaysAdded.get(displayId);

                        dispEl.addContent("\n            "); // to make it more readable...
                        dispEl.addChildElement(lineEl);
                        dispEl.addContent("\n        "); // to make it more readable...

                        String titleDisp = dispEl.getAttributeValue(LemsConstants.TITLE_ATTR);
                        dispEl.setAttributeValue(LemsConstants.TITLE_ATTR, titleDisp + ", " + plot.simPlot.getValuePlotted());
                        String ref = plot.simPlot.getValuePlotted();
                        ref = GeneralUtils.replaceAllTokens(ref, "/", "_");
                        ref = GeneralUtils.replaceAllTokens(ref, " ", "_");
                        ref = GeneralUtils.replaceAllTokens(ref, ":", "_");
                        if (project.generatedCellPositions.getNumberInAllCellGroups()>1)
                        {
                            ref = ref +"-"+ plot.simPlot.getCellGroup() + "_" + cellNum;
                        }
                        
                        lineEl.addAttribute(LemsConstants.ID_ATTR, ref);

                        String path = plot.simPlot.getCellGroup() + "[" + cellNum + "]/" + value;
                        String cellType = project.cellGroupsInfo.getCellType(plot.simPlot.getCellGroup());

                        if (version.isVersion2Latest())
                        {
                            lineEl.addAttribute(LemsConstants.TIMESCALE_ATTR, timescale);
                            path = plot.simPlot.getCellGroup() + "/" + cellNum + "/" + cellType + "/" + value;
                        }

                        lineEl.addAttribute(LemsConstants.QUANTITY_ATTR, path);

                        if (plot.simPlot.getValuePlotted().equals(SimPlot.VOLTAGE))
                        {
                            Units u = UnitConverter.voltageUnits[preferredUnits];
                            lineEl.addAttribute(LemsConstants.SCALE_ATTR, "1 " + u.getNeuroML2Symbol());
                        }
                        else if (plot.simPlot.getValuePlotted().endsWith("tau"))
                        {
                            Units u = UnitConverter.timeUnits[preferredUnits];
                            lineEl.addAttribute(LemsConstants.SCALE_ATTR, "1 " + u.getNeuroML2Symbol());
                        }
                        else if (plot.simPlot.getValuePlotted().indexOf(SimPlot.PLOTTED_VALUE_SEPARATOR + SimPlot.CONCENTRATION + SimPlot.PLOTTED_VALUE_SEPARATOR) >= 0)
                        {
                            Units u = UnitConverter.concentrationUnits[preferredUnits];
                            lineEl.addAttribute(LemsConstants.SCALE_ATTR, "1 " + u.getNeuroML2Symbol());
                        }
                        else if (plot.simPlot.getValuePlotted().indexOf(SimPlot.PLOTTED_VALUE_SEPARATOR + SimPlot.COND_DENS + SimPlot.PLOTTED_VALUE_SEPARATOR) >= 0)
                        {
                            Units u = UnitConverter.conductanceDensityUnits[preferredUnits];
                            lineEl.addAttribute(LemsConstants.SCALE_ATTR, "1 " + u.getNeuroML2Symbol());
                        }
                        else if (plot.simPlot.getValuePlotted().indexOf(SimPlot.PLOTTED_VALUE_SEPARATOR + SimPlot.CURR_DENS + SimPlot.PLOTTED_VALUE_SEPARATOR) >= 0)
                        {
                            Units u = UnitConverter.currentDensityUnits[preferredUnits];
                            lineEl.addAttribute(LemsConstants.SCALE_ATTR, "1 " + u.getNeuroML2Symbol());
                        }
                        else if (plot.simPlot.getValuePlotted().indexOf(SimPlot.PLOTTED_VALUE_SEPARATOR + SimPlot.REV_POT + SimPlot.PLOTTED_VALUE_SEPARATOR) >= 0)
                        {
                            Units u = UnitConverter.voltageUnits[preferredUnits];
                            lineEl.addAttribute(LemsConstants.SCALE_ATTR, "1 " + u.getNeuroML2Symbol());
                        }
                        else
                        {
                            lineEl.addAttribute(LemsConstants.SCALE_ATTR, "1");   //TODO: check for units...
                        }

                        String colourHex = getNextColourHex(displayId);
                        //String colourHex = getNextColourHex("All different colours...");

                        lineEl.addAttribute(LemsConstants.COLOR_ATTR, "#" + colourHex);

                        if (plot.simPlot.toBeSaved())
                        {
                            String datFile = plot.simPlot.getCellGroup() + "_" + cellNum + ".dat";
                            if (!plot.simPlot.isVoltage())
                            {
                                datFile = plot.simPlot.getCellGroup() + "_" + cellNum + "." + plot.simPlot.getSafeVarName() + ".dat";
                            }

                            File fullFile = new File(simDir, datFile);
                            String fileStr = fullFile.getAbsolutePath();
                            fileStr = fileStr.replaceAll("\\\\", "\\\\\\\\");
                            
                            if (version.isVersion2alpha())
                            {
                                lineEl.addAttribute(LemsConstants.SAVE_ATTR, fileStr);
                            }
                            else
                            {
                                SimpleXMLElement outputFileEl = new SimpleXMLElement(LemsConstants.OUTPUT_FILE_ELEMENT);
                                
                                outputFileEl.addAttribute(LemsConstants.ID_ATTR, plot.simPlot.getPlotReference()+"_OF");
                                outputFileEl.addAttribute(LemsConstants.OUTPUT_FILENAME_ATTR, fileStr);
                                
                                SimpleXMLElement outputColEl = new SimpleXMLElement(LemsConstants.OUTPUT_COLUMN_ELEMENT);
                                String id = path;
                                if (path.lastIndexOf("/")>=0)
                                    id = path.substring(path.lastIndexOf("/")+1);
                                outputColEl.addAttribute(LemsConstants.ID_ATTR, id);
                                outputColEl.addAttribute(LemsConstants.QUANTITY_ATTR, path);
                                
                                outputFileEl.addContent("\n            "); // to make it more readable...
                                outputFileEl.addChildElement(outputColEl);
                                outputFileEl.addContent("\n        "); // to make it more readable...
                                
                                outputFilesAdded.add(outputFileEl);
                       
                            }

                        }
                        logger.logComment("Adding line: " + lineEl.getXMLString("", false));
                    }
                }
            }
            
            for (SimpleXMLElement sxe: outputFilesAdded)
            {
                simEl.addContent("\n        "); // to make it more readable...
                simEl.addChildElement(sxe);
                simEl.addContent("\n    "); // to make it more readable...
            }


            lemsElement.addContent("\n\n"); // to make it more readable...

            GeneralUtils.writeShortFile(lemsFile, lemsElement.getXMLString("", false));

            String runFileName = "runsim.sh";
            String nml2ExeName = "./nml2";

            if (GeneralUtils.isWindowsBasedPlatform())
            {
                runFileName = "runsim.bat";
                nml2ExeName = ProjectStructure.getNeuroML2Dir().getAbsolutePath() + "\\nml2.bat";
                if (version.isVersion2Latest())
                    nml2ExeName = ProjectStructure.getjNeuroMLDir().getAbsolutePath()+"\\jnml.bat";
                    
            }

            File runFile = new File(generateDir, runFileName);

            StringBuilder runScript = new StringBuilder();

            if (version.isVersion2Latest())
            {
                runScript.append("export JNML_HOME=" + ProjectStructure.getjNeuroMLDir().getAbsolutePath() + "\n");
                nml2ExeName = ProjectStructure.getjNeuroMLDir().getAbsolutePath()+"/jnml";
            }
            else
            {
                runScript.append("cd " + ProjectStructure.getNeuroML2Dir().getAbsolutePath() + "\n");
            }


            runScript.append(nml2ExeName + " " + lemsFile.getAbsolutePath());

            if (lemsOption.equals(LemsOption.GENERATE_GRAPH))
            {
                runScript.append(" -graph");
            }
            else if (lemsOption.equals(LemsOption.GENERATE_NINEML))
            {
                runScript.append(" -nineml");
            }
            else if (lemsOption.equals(LemsOption.GENERATE_NEURON))
            {
                runScript.append(" -neuron");
            }

            if (runInBackground)
            {
                runScript.append(" -nogui");
            }

            runScript.append("\n");

            GeneralUtils.writeShortFile(runFile, runScript.toString());
            
            if (!lemsOption.equals(LemsOption.LEMS_WITHOUT_EXECUTE_MODEL))
            {

                Runtime rt = Runtime.getRuntime();
                // bit of a hack...
                rt.exec(new String[]
                {
                    "chmod", "u+x", runFile.getAbsolutePath()
                });

                String executable = runFile.getAbsolutePath();

                if (GeneralUtils.isWindowsBasedPlatform())
                {
                    executable = GeneralProperties.getExecutableCommandLine() + " " + nml2ExeName + " " + lemsFile.getAbsolutePath();
                }
                File dirToRunIn = ProjectStructure.getNeuroML2Dir();


                File positionsFile = new File(simDir, SimulationData.POSITION_DATA_FILE);
                File netConnsFile = new File(simDir, SimulationData.NETCONN_DATA_FILE);
                File elecInputFile = new File(simDir, SimulationData.ELEC_INPUT_DATA_FILE);
                try
                {
                    project.generatedCellPositions.saveToFile(positionsFile);
                    project.generatedNetworkConnections.saveToFile(netConnsFile);
                    project.generatedElecInputs.saveToFile(elecInputFile);
                }
                catch (IOException ex)
                {
                    GuiUtils.showErrorMessage(logger,
                                              "Problem saving generated positions in file: " + positionsFile.getAbsolutePath(),
                                              ex, null);
                    return;
                }

                // Saving summary of the simulation params
                try
                {
                    String sim = "LEMS";
                    if (version.isVersion2alpha())
                        sim = "LEMSalpha";
                    SimulationsInfo.recordSimulationSummary(project, simConf, simDir, sim, null, units);
                }
                catch (IOException ex2)
                {
                    GuiUtils.showErrorMessage(logger, "Error when trying to save a summary of the simulation settings in dir: " + simDir
                                                      + "\nThere will be less info on this simulation in the previous simulation browser dialog", ex2, null);
                }


                logger.logComment("Going to execute: " + executable + " in dir: "
                                  + dirToRunIn, true);

                //Process process = rt.exec(executable, null, dirToRunIn);
                ucl.physiol.neuroconstruct.hpc.utils.ProcessManager.runCommand(executable, "LEMS", 5, dirToRunIn);

                if (lemsOption.equals(LemsOption.GENERATE_GRAPH))
                {
                    File imageFile = new File(generateDir, lemsFileName.replace(".xml", ".png"));
                    logger.logComment("Searching for image: " + imageFile.getAbsolutePath(), true);

                    if (imageFile.exists())
                    {
                        GuiUtils.showImage(imageFile);
                    }
                    else
                    {
                        try
                        {
                            Thread.sleep(5000);
                            GuiUtils.showImage(imageFile);
                        }
                        catch (Exception ex)
                        {
                            GuiUtils.showErrorMessage(logger, "Problem displaying file: " + imageFile, ex, null);
                        }
                    }
                }
            }

        }

    }

    private String convertValue(String val)
    {
        logger.logComment("Converting val: " + val);
        if (val.equals(SimPlot.VOLTAGE))
        {
            return "v";
        }
        else if (val.split(":").length == 2)  // TODO: Make more general!!!!
        {
            String cmName = val.split(":")[0];
            String varName = val.split(":")[1];
            varName = varName.replaceAll("_", "/");

            if (!varName.endsWith("inf") && !varName.endsWith("tau"))
            {
                if (!varName.endsWith("/q"))
                {
                    varName = varName + "/q";
                }
            }
            else
            {
                varName = varName.replaceAll("inf", "/inf");
                varName = varName.replaceAll("tau", "/tau");
            }

            return "biophys/membraneProperties/" + cmName + "_all/" + cmName + "/" + varName;
        }
        else if (val.split(":").length == 3)  // TODO: Make more general!!!!
        {
            String cmName = val.split(":")[0];
            String type = val.split(":")[1];
            if (type.equals(SimPlot.CONCENTRATION))
            {
                String ion = val.split(":")[2];
                //return "biophys/intracellularProperties/"+ion+"/concentration";
                return ion + "Conc";

            }
            else if (type.equals(SimPlot.COND_DENS))
            {
                String ion = val.split(":")[2];
                //return "biophys/intracellularProperties/"+ion+"/concentration";
                return "biophys/membraneProperties/" + cmName + "_all/gDensity";

            }
            else if (type.equals(SimPlot.REV_POT))
            {
                String ion = val.split(":")[2];
                //return "biophys/intracellularProperties/"+ion+"/concentration";
                return "biophys/membraneProperties/" + cmName + "_all/erev";

            }
            else if (type.equals(SimPlot.CURR_DENS))
            {
                String ion = val.split(":")[2];
                //return "biophys/intracellularProperties/"+ion+"/concentration";
                return "biophys/membraneProperties/" + cmName + "_all/iDensity";

            }
        }

        return val;
    }

    public int getCurrentRandomSeed()
    {
        return this.randomSeed;
    }

    public float getCurrentGenTime()
    {
        return this.genTime;
    }

    public static String getFileHeader()
    {
        StringBuilder response = new StringBuilder();
        response.append("//  ******************************************************\n");
        response.append("// \n");
        response.append("//     File generated by: neuroConstruct v" + GeneralProperties.getVersionNumber() + "\n");
        response.append("// \n");
        response.append("//  ******************************************************\n");

        response.append("\n");
        return response.toString();
    }

    public String getNextColourHex(String plotFrame)
    {
        if (!nextColour.containsKey(plotFrame))
        {
            nextColour.put(plotFrame, 1);
        }
        int colNum = nextColour.get(plotFrame);

        String colour = ColourUtils.getSequentialColourHex(colNum);
        int newColour = colNum + 1;
        if (newColour >= 10)
        {
            newColour = 1;
        }

        nextColour.put(plotFrame, newColour);

        return colour;
    }

    /*
     * @TODO Should be moved to libNeuroML!!
     
    */

    /*
     * TODO: replace with function in org.neuroml.model!!
     */
    public static boolean validateAgainstNeuroML2alphaSchema(File nmlFile)
    {
        File schemaFile = GeneralProperties.getNeuroMLv2alphaSchemaFile();

        return XMLUtils.validateAgainstSchema(nmlFile, schemaFile);
    }

    /*
     * TODO: replace with function in org.neuroml.model!!
     */
    public static boolean validateAgainstNeuroML2beta1Schema(File nmlFile)
    {
        File schemaFile = GeneralProperties.getNeuroMLv2beta1SchemaFile();

        return XMLUtils.validateAgainstSchema(nmlFile, schemaFile);

    }

    /*
     * TODO: replace with function in org.neuroml1.model!!
     */
    public static boolean validateAgainstLatestNeuroML1Schema(File nmlFile)
    {
        File schemaFile = GeneralProperties.getNeuroMLSchemaFile();

        return XMLUtils.validateAgainstSchema(nmlFile, schemaFile);

    }
    

    public static void main(String[] args)
    {
        System.out.println("Testing NeuroMLFileManager...");
        try
        {
            NeuroMLVersion version = NeuroMLVersion.getLatestVersion();
            //version = NeuroMLVersion.NEUROML_VERSION_2_ALPHA;
            
            //String units = UnitConverter.getUnitSystemDescription(UnitConverter.GENESIS_PHYSIOLOGICAL_UNITS);
            String units = UnitConverter.getUnitSystemDescription(UnitConverter.GENESIS_SI_UNITS);
            
            //File projFile = new File("osb/showcase/neuroConstructShowcase/Ex10_NeuroML2/Ex10_NeuroML2.ncx");
            //File projFile = new File("models/LarkumEtAl2009/LarkumEtAl2009.ncx");
            File projFile = new File("osb/cerebellum/cerebellar_granule_cell/GranuleCell/neuroConstruct/GranuleCell.ncx");
            //projFile = new File("osb/cerebellum/cerebellar_granule_cell/GranuleCellVSCS/neuroConstruct/GranuleCellVSCS.ncx");
            //projFile = new File("nCmodels/RothmanEtAl_KoleEtAl_PyrCell/RothmanEtAl_KoleEtAl_PyrCell.ncx");
            //projFile = new File("../nC_projects/Thaal/Thaal.ncx");
            //File projFile = new File("osb/hippocampus/CA1_pyramidal_neuron/CA1PyramidalCell/neuroConstruct/CA1PyramidalCell.ncx");
            //File projFile = new File("osb/showcase/neuroConstructShowcase/Ex4_HHcell/Ex4_HHcell.ncx");
            //File projFile = new File("osb/cerebral_cortex/networks/ACnet2/neuroConstruct/ACnet2.ncx");
            
            
            Project p = Project.loadProject(projFile, null);
            //Proje
            ProjectManager pm = new ProjectManager(null, null);
            pm.setCurrentProject(p);

            String simConf = SimConfigInfo.DEFAULT_SIM_CONFIG_NAME;

            ///simConf = "GranCellTested";

            if (projFile.getName().startsWith("GranuleCellVSCS"))
            {
                simConf = "OneChanBothSims";
            }
            else if (projFile.getName().startsWith("LemsTest"))
            {
                simConf = "GranCell";
                simConf = "GranCellTested";
                //simConf = "MainenCell";
            }
            else if (projFile.getName().startsWith("RothmanEtAl_KoleEtAl_PyrCell"))
            {
                simConf = "TestChannelML";
            }
            else if (projFile.getName().startsWith("LarkumEtAl2009"))
            {
                simConf = "TestIndividualChannels";
            }
            else if (projFile.getName().startsWith("ACnet2"))
            {
                simConf = "TestChannels";
                p.simulationParameters.setDt(0.01f);
            }
            /*else if (projFile.getName().startsWith("Ex10_NeuroML2"))
            {
                simConf = "Test NeuroML2 ionChannel";
            }*/


            String ref = "LEMS_test";
            ref= ref + (version.isVersion2betaOrLater() ? "_beta":"_alpha");
            ref= ref + (units.equals("GENESIS SI Units") ? "_SI":"_PHYS");
            p.simulationParameters.setReference(ref);

            pm.doGenerate(simConf, 123);

            Thread.sleep(1000);

            System.out.println("Generated cells: " + p.generatedCellPositions.details());

            NeuroMLFileManager nmlFM = new NeuroMLFileManager(p);


            OriginalCompartmentalisation oc = new OriginalCompartmentalisation();

            SimConfig sc = p.simConfigInfo.getSimConfig(simConf);

            //LemsOption lo = LemsOption.GENERATE_GRAPH;
            //LemsOption lo = LemsOption.NONE;
            LemsOption lo = LemsOption.EXECUTE_MODEL;
            
            
            File neuroMLDir = ProjectStructure.getNeuroMLDir(p.getProjectMainDirectory(), version);

            nmlFM.generateNeuroMLFiles(sc, version, lo, oc, 123, false, false, neuroMLDir, units, false);

            //File tempDir = new File(projFile.getParentFile(), "temp");

            //npfm.generateNeuroMLFiles(sc, NeuroMLVersion.NEUROML_VERSION_1, false, oc, 123, false, false, tempDir);

            //gen.runGenesisFile();

            //NeuronFileManager nfm = new

            if (simConf.equals("TestIndividualChannels"))
            {
                pm.doGenerate(simConf, 123);

                p.simulationParameters.setReference("NEURON_test");

                p.neuronFileManager.generateTheNeuronFiles(sc, null, NeuronFileManager.RUN_HOC, 1234);

                System.out.println("Generated: " + p.neuronFileManager.getMainHocFile());
                File mainHoc = p.neuronFileManager.getMainHocFile();

                System.out.println("Created hoc files, including: " + mainHoc);

                ucl.physiol.neuroconstruct.nmodleditor.processes.ProcessManager prm = new ucl.physiol.neuroconstruct.nmodleditor.processes.ProcessManager(mainHoc);

                boolean success = prm.compileFileWithNeuron(true, false);

                System.out.println("Compiled NEURON files: " + success);

                if (success)
                {
                    pm.doRunNeuron(sc);
                }
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }
}
