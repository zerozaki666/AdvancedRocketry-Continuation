package zmaster587.advancedRocketry.util;

import net.minecraft.block.Block;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTException;
import net.minecraftforge.common.BiomeManager.BiomeEntry;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import zmaster587.advancedRocketry.AdvancedRocketry;
import zmaster587.advancedRocketry.api.Configuration;
import zmaster587.advancedRocketry.api.dimension.IDimensionProperties;
import zmaster587.advancedRocketry.api.dimension.solar.BlackHoleProperties;
import zmaster587.advancedRocketry.api.dimension.solar.IGalaxy;
import zmaster587.advancedRocketry.api.dimension.solar.StellarBody;
import zmaster587.advancedRocketry.dimension.AtmosphereVisualProperties;
import zmaster587.advancedRocketry.dimension.DimensionManager;
import zmaster587.advancedRocketry.dimension.DimensionProperties;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class XMLPlanetLoader {

	Document doc;
	NodeList currentList;
	int currentNodeIndex;
	int starId;
	int offset;

	HashMap<StellarBody, Integer> maxPlanetNumber = new HashMap<StellarBody, Integer>();
	HashMap<StellarBody, Integer> maxGasPlanetNumber = new HashMap<StellarBody, Integer>();

	public boolean loadFile(File xmlFile) throws IOException {
		DocumentBuilder docBuilder;
		doc = null;
		try {
			docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		} catch (ParserConfigurationException e) {
			return false;
		}

		try {
			doc = docBuilder.parse(xmlFile);
		} catch (SAXException e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}
	public XMLPlanetLoader() {
		doc = null;
		currentNodeIndex = -1;
		starId=0;
	}

	public boolean isValid() {
		return doc != null;
	}

	public int getMaxNumPlanets(StellarBody body) {
		return maxPlanetNumber.get(body);
	}


	public int getMaxNumGasGiants(StellarBody body) {
		return maxGasPlanetNumber.get(body);
	}

	private List<DimensionProperties> readPlanetFromNode(Node planetNode, StellarBody star) {
		List<DimensionProperties> list = new ArrayList<DimensionProperties>();
		Node planetPropertyNode = planetNode.getFirstChild();


		DimensionProperties properties = new DimensionProperties(DimensionManager.getInstance().getNextFreeDim(offset));

		if(properties == null)
			return list;
		list.add(properties);
		offset++;//Increment for dealing with child planets


		//Set name for dimension if exists
		if(planetNode.hasAttributes()) {
			Node nameNode = planetNode.getAttributes().getNamedItem("name");
			if(nameNode != null && !nameNode.getNodeValue().isEmpty()) {
				properties.setName(nameNode.getNodeValue());
			}

			nameNode = planetNode.getAttributes().getNamedItem("DIMID");
			if(nameNode != null && !nameNode.getNodeValue().isEmpty()) {
				try {
					if(nameNode.getTextContent().isEmpty()) throw new NumberFormatException();
					int explicitId = Integer.parseInt(nameNode.getTextContent());
					properties.setId(explicitId);
					//We're not using the offset so decrement to prepare for next planet
					offset--;
				} catch (NumberFormatException e) {
					AdvancedRocketry.logger.warn("Invalid DIMID specified for planet " + properties.getName()); //TODO: more detailed error msg
					list.remove(properties);
					offset--;
					return list;
				}
			}

			nameNode = planetNode.getAttributes().getNamedItem("dimMapping");
			if(nameNode != null) {
				properties.isNativeDimension = false;
			}

			nameNode = planetNode.getAttributes().getNamedItem("customIcon");
			if(nameNode != null) {
				properties.customIcon = nameNode.getTextContent();
			}
		}

		while(planetPropertyNode != null) {
			if(planetPropertyNode.getNodeName().equalsIgnoreCase("fogcolor")) {
				String[] colors = planetPropertyNode.getTextContent().split(",");
				try {
					if(colors.length >= 3) {
						float rgb[] = new float[3];


						for(int j = 0; j < 3; j++)
							rgb[j] = Float.parseFloat(colors[j]);
						properties.fogColor = rgb;

					}
					else if(colors.length == 1) {
						int cols = Integer.parseUnsignedInt(colors[0].substring(2), 16);
						float rgb[] = new float[3];

						rgb[0] = ((cols >>> 16) & 0xff) / 255f;
						rgb[1] = ((cols >>> 8) & 0xff) / 255f;
						rgb[2] = (cols & 0xff) / 255f;

						properties.fogColor = rgb;
					}
					else
						AdvancedRocketry.logger.warn("Invalid number of floats specified for fog color (Required 3, comma sperated)"); //TODO: more detailed error msg
				} catch (NumberFormatException e) {
					AdvancedRocketry.logger.warn("Invalid fog color specified"); //TODO: more detailed error msg
				}
			}
			else if(planetPropertyNode.getNodeName().equalsIgnoreCase("gas")) {
				Fluid f = FluidRegistry.getFluid(planetPropertyNode.getTextContent());
				
				if(f == null)
					AdvancedRocketry.logger.warn( "\"" + planetPropertyNode.getTextContent() + "\" is not a valid fluid"); //TODO: more detailed error msg
				else {
					properties.getHarvestableGasses().add(f);
				}
			}
			else if(planetPropertyNode.getNodeName().equalsIgnoreCase("oceanBlock")) {
				String blockName = planetPropertyNode.getTextContent();
				Block block = (Block) Block.blockRegistry.getObject(blockName);
				
				if(block == Blocks.air || block == null)
					AdvancedRocketry.logger.warn("Invalid ocean block: " + blockName); //TODO: more detailed error msg
				
				properties.setOceanBlock(block);
			}
			else if(planetPropertyNode.getNodeName().equalsIgnoreCase("fillerBlock")) {
				String blockName = planetPropertyNode.getTextContent();
				Block block = (Block) Block.getBlockFromName(blockName);
				
				if(block == Blocks.air || block == null)
				{
					AdvancedRocketry.logger.warn("Invalid filler block: " + blockName); //TODO: more detailed error msg
					block = null;
				}
				
				properties.setStoneBlock(block);
			}
			else if(planetPropertyNode.getNodeName().equalsIgnoreCase("skycolor")) {
				String[] colors = planetPropertyNode.getTextContent().split(",");
				try {

					if(colors.length >= 3) {
						float rgb[] = new float[3];

						for(int j = 0; j < 3; j++)
							rgb[j] = Float.parseFloat(colors[j]);
						properties.skyColor = rgb;

					}
					else if(colors.length == 1) {
						int cols = Integer.parseUnsignedInt(colors[0].substring(2), 16);
						float rgb[] = new float[3];

						rgb[0] = ((cols >>> 16) & 0xff) / 255f;
						rgb[1] = ((cols >>> 8) & 0xff) / 255f;
						rgb[2] = (cols & 0xff) / 255f;

						properties.skyColor = rgb;
					}
					else
						AdvancedRocketry.logger.warn("Invalid number of floats specified for sky color (Required 3, comma sperated)"); //TODO: more detailed error msg

				} catch (NumberFormatException e) {
					AdvancedRocketry.logger.warn("Invalid sky color specified"); //TODO: more detailed error msg
				}
			}
			else if(planetPropertyNode.getNodeName().equalsIgnoreCase("atmosphereDensity")) {

				try {
					properties.setAtmosphereDensityDirect(Math.min(Math.max(Integer.parseInt(planetPropertyNode.getTextContent()), DimensionProperties.MIN_ATM_PRESSURE), DimensionProperties.MAX_ATM_PRESSURE));
				} catch (NumberFormatException e) {
					AdvancedRocketry.logger.warn("Invalid atmosphereDensity specified"); //TODO: more detailed error msg
				}
			}
			else if(planetPropertyNode.getNodeName()
					.equalsIgnoreCase("atmosphereRendering")) {
				properties.setAtmosphereVisualProperties(
						readAtmosphereRendering(
								planetPropertyNode,
								properties.getName()));
			}
			else if(planetPropertyNode.getNodeName().equalsIgnoreCase("gravitationalmultiplier")) {

				try {
					properties.gravitationalMultiplier = Math.min(Math.max(Integer.parseInt(planetPropertyNode.getTextContent()), DimensionProperties.MIN_GRAVITY), DimensionProperties.MAX_GRAVITY)/100f;
				} catch (NumberFormatException e) {
					AdvancedRocketry.logger.warn("Invalid gravitationalMultiplier specified"); //TODO: more detailed error msg
				}
			}
			else if(planetPropertyNode.getNodeName().equalsIgnoreCase("orbitaldistance")) {

				try {
					properties.orbitalDist = Math.min(Math.max(Integer.parseInt(planetPropertyNode.getTextContent()), DimensionProperties.MIN_DISTANCE), DimensionProperties.MAX_DISTANCE);
				} catch (NumberFormatException e) {
					AdvancedRocketry.logger.warn("Invalid orbitalDist specified"); //TODO: more detailed error msg
				}
			}
			else if(planetPropertyNode.getNodeName().equalsIgnoreCase("mass")) {
				try {
					properties.mass = Math.max(
							Float.parseFloat(planetPropertyNode.getTextContent()), 0.01F);
				} catch (NumberFormatException e) {
					AdvancedRocketry.logger.warn("Invalid planet mass specified");
				}
			}
			else if(planetPropertyNode.getNodeName().equalsIgnoreCase("orbitaltheta")) {

				try {
					properties.baseOrbitTheta = (Integer.parseInt(planetPropertyNode.getTextContent()) % 360) * Math.PI/180f;
				} catch (NumberFormatException e) {
					AdvancedRocketry.logger.warn("Invalid orbitalPhi specified"); //TODO: more detailed error msg
				}
			}
			else if(planetPropertyNode.getNodeName().equalsIgnoreCase("rotationalperiod")) {
				try {
					int rotationalPeriod =  Integer.parseInt(planetPropertyNode.getTextContent());
					if(properties.rotationalPeriod > 0)
						properties.rotationalPeriod = rotationalPeriod;
					else
						AdvancedRocketry.logger.warn("rotational Period must be greater than 0"); //TODO: more detailed error msg
				} catch (NumberFormatException e) {
					AdvancedRocketry.logger.warn("Invalid rotational period specified"); //TODO: more detailed error msg
				}
			}
			else if(planetPropertyNode.getNodeName().equalsIgnoreCase("seaLevel")) {
				try {
					properties.setSeaLevel(Integer.parseInt(planetPropertyNode.getTextContent()));
				} catch (NumberFormatException e) {
					AdvancedRocketry.logger.warn("Invalid sealevel specified"); //TODO: more detailed error msg
				}
			}
			else if(planetPropertyNode.getNodeName().equalsIgnoreCase("biomeids")) {

				String biomeList[] = planetPropertyNode.getTextContent().split(",");
				for(int j = 0; j < biomeList.length; j++) {
					try {
						int biome =  Integer.parseInt(biomeList[j]);

						if(!properties.addBiome(biome))
							AdvancedRocketry.logger.warn(biomeList[j] + " is not a valid biome id"); //TODO: more detailed error msg
					} catch (NumberFormatException e) {
						AdvancedRocketry.logger.warn(biomeList[j] + " is not a valid biome id"); //TODO: more detailed error msg
					}
				}
			}
			else if(planetPropertyNode.getNodeName().equalsIgnoreCase("artifact")) {
				ItemStack stack = XMLPlanetLoader.getStack(planetPropertyNode.getTextContent());

				if(stack != null)
					properties.getRequiredArtifacts().add(stack);
			}
			else if(planetPropertyNode.getNodeName().equalsIgnoreCase("planet")) {
				List<DimensionProperties> childList = readPlanetFromNode(planetPropertyNode, star);
				if(childList.size() > 0) {
					DimensionProperties child = childList.get(childList.size()-1); // Last entry in the list is the child planet
					properties.addChildPlanet(child);
					list.addAll(childList);
				}
			}
			else if(planetPropertyNode.getNodeName().equalsIgnoreCase("orbitalPhi")) {
				try {
					properties.orbitalPhi = (Integer.parseInt(planetPropertyNode.getTextContent()) % 360);
				} catch (NumberFormatException e) {
					AdvancedRocketry.logger.warn("Invalid orbitalTheta specified"); //TODO: more detailed error msg
				}
			}
			else if(planetPropertyNode.getNodeName().equalsIgnoreCase("oreGen")) {
				properties.oreProperties = XMLOreLoader.loadOre(planetPropertyNode);
			}
			else if(planetPropertyNode.getNodeName().equalsIgnoreCase("genType")) {
				try {
					properties.setGenType(Integer.parseInt(planetPropertyNode.getTextContent()));
				} catch (NumberFormatException e) {
					AdvancedRocketry.logger.warn("Invalid generator type specified"); //TODO: more detailed error msg
				}
			}
			else if(planetPropertyNode.getNodeName().equalsIgnoreCase("hasRings"))
				properties.hasRings = Boolean.parseBoolean(planetPropertyNode.getTextContent());
			else if(planetPropertyNode.getNodeName().equalsIgnoreCase("ringColor")) {
				String[] colors = planetPropertyNode.getTextContent().split(",");
				try {

					if(colors.length >= 3) {
						float rgb[] = new float[3];

						for(int j = 0; j < 3; j++)
							rgb[j] = Float.parseFloat(colors[j]);
						properties.ringColor = rgb;

					}
					else if(colors.length == 1) {
						int cols = Integer.parseUnsignedInt(colors[0].substring(2), 16);
						float rgb[] = new float[3];

						rgb[0] = ((cols >>> 16) & 0xff) / 255f;
						rgb[1] = ((cols >>> 8) & 0xff) / 255f;
						rgb[2] = (cols & 0xff) / 255f;

						properties.ringColor = rgb;
					}
					else
						AdvancedRocketry.logger.warn("Invalid number of floats specified for ring color (Required 3, comma sperated)"); //TODO: more detailed error msg

				} catch (NumberFormatException e) {
					AdvancedRocketry.logger.warn("Invalid sky color specified"); //TODO: more detailed error msg
				}
			}
			else if(planetPropertyNode.getNodeName().equalsIgnoreCase("hasOxygen")) {
				String text = planetPropertyNode.getTextContent();
				if(text != null && !text.isEmpty() && text.equalsIgnoreCase("false"))
					properties.hasOxygen = false;
			}
			else if(planetPropertyNode.getNodeName().equalsIgnoreCase("GasGiant")) {
				String text = planetPropertyNode.getTextContent();
				if(text != null && !text.isEmpty() && text.equalsIgnoreCase("true"))
					properties.setGasGiant(true);
			}else if(planetPropertyNode.getNodeName().equalsIgnoreCase("spawnable")) {
				int weight = 100;
				int groupMin = 1, groupMax = 1;
				String nbtString = "";
				Node weightNode = planetPropertyNode.getAttributes().getNamedItem("weight");
				Node groupMinNode = planetPropertyNode.getAttributes().getNamedItem("groupMin");
				Node groupMaxNode = planetPropertyNode.getAttributes().getNamedItem("groupMax");
				Node nbtNode = planetPropertyNode.getAttributes().getNamedItem("nbt");

				//Get spawn properties
				if(weightNode != null) {
					try {
						weight = Integer.parseInt(weightNode.getTextContent());
						weight = Math.max(1, weight);
					} catch(NumberFormatException ignored) {
					}
				}
				if(groupMinNode != null) {
					try {
						groupMin = Integer.parseInt(groupMinNode.getTextContent());
						groupMin = Math.max(1, groupMin);
					} catch(NumberFormatException ignored) {
					}
				}
				if(groupMaxNode != null) {
					try {
						groupMax = Integer.parseInt(groupMaxNode.getTextContent());
						groupMax = Math.max(1, groupMax);
					} catch(NumberFormatException ignored) {
					}
				}

				if(nbtNode != null) {
					nbtString = nbtNode.getTextContent();
				}

				if (groupMax < groupMin) {
					groupMax = groupMin;
				}

				String entityIdentifier = planetPropertyNode.getTextContent() == null
						? ""
						: planetPropertyNode.getTextContent().trim();
				Class clazz = (Class)EntityList.stringToClassMapping.get(entityIdentifier);
				//If not using string name maybe it's a class name?
				if(clazz == null) {
					try {
						clazz = Class.forName(entityIdentifier);
						if(!EntityLiving.class.isAssignableFrom(clazz))
							clazz = null;
					} catch (Exception ignored) {}
				}

				if(clazz != null && EntityLiving.class.isAssignableFrom(clazz)) {
					SpawnListEntryNBT entry = new SpawnListEntryNBT(
							(Class<? extends EntityLiving>)clazz, weight, groupMin,
							groupMax, entityIdentifier);
					boolean validEntry = true;
					if(!nbtString.isEmpty())
						try {
							entry.setNbt(nbtString);
						} catch (DOMException e) {
							validEntry = false;
							AdvancedRocketry.logger.fatal("===== Configuration Error!  Please check your save's planetDefs.xml config file =====\n"
									+ e.getLocalizedMessage()
									+ "\nThe following is not valid JSON:\n" + nbtString);
						} catch (NBTException e) {
							validEntry = false;
							AdvancedRocketry.logger.fatal("===== Configuration Error!  Please check your save's planetDefs.xml config file =====\n"
									+ e.getLocalizedMessage()
									+ "\nThe following is not valid NBT data:\n" + nbtString);
						}

					if(validEntry)
						properties.getSpawnListEntries().add(entry);
				} else
					AdvancedRocketry.logger.warn("Cannot find " + entityIdentifier + " while registering entity for planet spawn");

			} else if(planetPropertyNode.getNodeName().equalsIgnoreCase("isKnown")) {
				String text = planetPropertyNode.getTextContent();
				if(text != null && !text.isEmpty() && text.equalsIgnoreCase("true")) {
					Configuration.initiallyKnownPlanets.add(properties.getId());
				}
			}

			planetPropertyNode = planetPropertyNode.getNextSibling();
		}

		//Star may not be registered at this time, use ID version instead
		properties.setStar(star.getId());

		//Set peak insolation multiplier
		//Assumes that a 16 atmosphere is 16x the partial pressure but not thicker, because I don't want to deal with that and this is fairly simple right now
		//Get what it would be relative to LEO, this gives ~0.76 for Earth at the surface
		double insolationRelativeToLEO = AstronomicalBodyHelper.getStellarBrightness(star, properties.getSolarOrbitalDistance()) * Math.pow(Math.E, -(0.0026899d * properties.getAtmosphereDensity()));
		//Multiply by Earth LEO/Earth Surface for ratio relative to Earth surface (1360/1040)
		properties.peakInsolationMultiplier = insolationRelativeToLEO * 1.308d;

		//Set temperature
		properties.averageTemperature = AstronomicalBodyHelper.getAverageTemperature(star, properties.getSolarOrbitalDistance(), properties.getAtmosphereDensity());

		//If no biomes are specified add some!
		if(properties.getBiomes().isEmpty())
			properties.addBiomes(properties.getViableBiomes());

		return list;
	}

	private static AtmosphereVisualProperties readAtmosphereRendering(
			Node atmosphereNode, String planetName) {
		AtmosphereVisualProperties properties =
				new AtmosphereVisualProperties();
		Node versionNode = atmosphereNode.hasAttributes()
				? atmosphereNode.getAttributes().getNamedItem("version")
				: null;
		if(versionNode != null) {
			try {
				int version = Integer.parseInt(
						versionNode.getNodeValue().trim());
				if(version != AtmosphereVisualProperties.SCHEMA_VERSION) {
					warnAtmosphereXml(
							planetName, "version",
							"unsupported version " + version
									+ "; ignoring this rendering block");
					return properties;
				}
			}
			catch(IllegalArgumentException exception) {
				warnAtmosphereXml(
						planetName, "version",
						"invalid version; ignoring this rendering block");
				return properties;
			}
		}
		else {
			AdvancedRocketry.logger.warn(
					"Atmosphere rendering block for planet '"
							+ planetName
							+ "' has no version; interpreting it as version "
							+ AtmosphereVisualProperties.SCHEMA_VERSION);
		}

		Map<String, Node> overrideNodes = new HashMap<String, Node>();
		Node propertyNode = atmosphereNode.getFirstChild();
		while(propertyNode != null) {
			overrideNodes.put(
					propertyNode.getNodeName().toLowerCase(Locale.ROOT),
					propertyNode);
			propertyNode = propertyNode.getNextSibling();
		}

		// Apply geometry before layer-dependent values so XML child order does
		// not change validation results.
		String[] orderedTags = new String[] {
				"planetradiuskm",
				"atmosphereheightkm",
				"rayleighcolor",
				"rayleighstrength",
				"rayleighscaleheightkm",
				"miecolor",
				"miestrength",
				"miescaleheightkm",
				"mieanisotropy",
				"absorptioncolor",
				"absorptionstrength",
				"absorptioncenterkm",
				"absorptionwidthkm",
				"multiplescatteringstrength",
				"sunintensitymultiplier",
				"exposure",
				"cloudlayermode"
		};
		for(String orderedTag : orderedTags) {
			propertyNode = overrideNodes.get(orderedTag);
			if(propertyNode == null)
				continue;
			String tag = propertyNode.getNodeName();
			try {
				if(tag.equalsIgnoreCase("planetRadiusKm"))
					properties.setPlanetRadiusKm(
							parseAtmosphereDouble(propertyNode));
				else if(tag.equalsIgnoreCase("atmosphereHeightKm"))
					properties.setAtmosphereHeightKm(
							parseAtmosphereDouble(propertyNode));
				else if(tag.equalsIgnoreCase("rayleighColor"))
					properties.setRayleighColor(
							parseAtmosphereColor(propertyNode));
				else if(tag.equalsIgnoreCase("rayleighStrength"))
					properties.setRayleighStrength(
							parseAtmosphereDouble(propertyNode));
				else if(tag.equalsIgnoreCase("rayleighScaleHeightKm"))
					properties.setRayleighScaleHeightKm(
							parseAtmosphereDouble(propertyNode));
				else if(tag.equalsIgnoreCase("mieColor"))
					properties.setMieColor(
							parseAtmosphereColor(propertyNode));
				else if(tag.equalsIgnoreCase("mieStrength"))
					properties.setMieStrength(
							parseAtmosphereDouble(propertyNode));
				else if(tag.equalsIgnoreCase("mieScaleHeightKm"))
					properties.setMieScaleHeightKm(
							parseAtmosphereDouble(propertyNode));
				else if(tag.equalsIgnoreCase("mieAnisotropy"))
					properties.setMieAnisotropy(
							parseAtmosphereDouble(propertyNode));
				else if(tag.equalsIgnoreCase("absorptionColor"))
					properties.setAbsorptionColor(
							parseAtmosphereColor(propertyNode));
				else if(tag.equalsIgnoreCase("absorptionStrength"))
					properties.setAbsorptionStrength(
							parseAtmosphereDouble(propertyNode));
				else if(tag.equalsIgnoreCase("absorptionCenterKm"))
					properties.setAbsorptionCenterKm(
							parseAtmosphereDouble(propertyNode));
				else if(tag.equalsIgnoreCase("absorptionWidthKm"))
					properties.setAbsorptionWidthKm(
							parseAtmosphereDouble(propertyNode));
				else if(tag.equalsIgnoreCase(
						"multipleScatteringStrength"))
					properties.setMultipleScatteringStrength(
							parseAtmosphereDouble(propertyNode));
				else if(tag.equalsIgnoreCase(
						"sunIntensityMultiplier"))
					properties.setSunIntensityMultiplier(
							parseAtmosphereDouble(propertyNode));
				else if(tag.equalsIgnoreCase("exposure"))
					properties.setExposure(
							parseAtmosphereDouble(propertyNode));
				else if(tag.equalsIgnoreCase("cloudLayerMode"))
					properties.setCloudLayerMode(
							propertyNode.getTextContent());
			}
			catch(IllegalArgumentException exception) {
				warnAtmosphereXml(
						planetName, tag,
						exception.getMessage()
								+ "; using the legacy-derived fallback");
			}
		}

		return properties.sanitizedCopy(
				"planet '" + planetName + "'");
	}

	private static double parseAtmosphereDouble(Node node) {
		String value = node.getTextContent();
		if(value == null || value.trim().isEmpty())
			throw new IllegalArgumentException(
					"value cannot be empty");
		double parsed = Double.parseDouble(value.trim());
		if(Double.isNaN(parsed) || Double.isInfinite(parsed))
			throw new IllegalArgumentException(
					"value must be finite");
		return parsed;
	}

	private static float[] parseAtmosphereColor(Node node) {
		String value = node.getTextContent();
		if(value == null)
			throw new IllegalArgumentException(
					"RGB value cannot be empty");
		value = value.trim();

		if(value.startsWith("0x") || value.startsWith("0X")) {
			if(value.length() != 8)
				throw new IllegalArgumentException(
						"hex RGB must use 0xRRGGBB");
			int packed = Integer.parseInt(value.substring(2), 16);
			return new float[] {
					((packed >>> 16) & 0xff) / 255F,
					((packed >>> 8) & 0xff) / 255F,
					(packed & 0xff) / 255F
			};
		}

		String[] components = value.split(",", -1);
		if(components.length != 3)
			throw new IllegalArgumentException(
					"RGB value must contain exactly three components");
		float[] color = new float[3];
		for(int index = 0; index < color.length; index++) {
			String component = components[index].trim();
			if(component.isEmpty())
				throw new IllegalArgumentException(
						"RGB components cannot be empty");
			color[index] = Float.parseFloat(component);
			if(Float.isNaN(color[index])
					|| Float.isInfinite(color[index]))
				throw new IllegalArgumentException(
						"RGB components must be finite");
		}
		return color;
	}

	private static void warnAtmosphereXml(
			String planetName, String tag, String message) {
		AdvancedRocketry.logger.warn(
				"Invalid atmosphere rendering override for planet '"
						+ planetName + "', tag '" + tag + "': "
						+ message);
	}


	public StellarBody readStar(Node planetNode) {
		StellarBody star = readSubStar(planetNode);
		if(planetNode.hasAttributes()) {
			Node nameNode;

			nameNode = planetNode.getAttributes().getNamedItem("x");

			if(nameNode != null && !nameNode.getNodeValue().isEmpty()) {
				try {
					star.setPosX(Integer.parseInt(nameNode.getNodeValue()));
				} catch (NumberFormatException e) {
					AdvancedRocketry.logger.warn("Error Reading star " + star.getName());
				}
			}

			Node yNode = planetNode.getAttributes().getNamedItem("y");
			Node zNode = planetNode.getAttributes().getNamedItem("z");
			Node schemaNode = planetNode.getAttributes().getNamedItem("coordinateSchema");
			boolean xyzSchema = zNode != null
					|| (schemaNode != null && "xyz".equalsIgnoreCase(schemaNode.getNodeValue()));

			if(yNode != null && !yNode.getNodeValue().isEmpty()) {
				try {
					if(xyzSchema)
						star.setPosY(Integer.parseInt(yNode.getNodeValue()));
					else
						star.setPosZ(Integer.parseInt(yNode.getNodeValue()));
				} catch (NumberFormatException e) {
					AdvancedRocketry.logger.warn("Error Reading star " + star.getName());
				}
			}

			if(zNode != null && !zNode.getNodeValue().isEmpty()) {
				try {
					star.setPosZ(Integer.parseInt(zNode.getNodeValue()));
				} catch (NumberFormatException e) {
					AdvancedRocketry.logger.warn("Error Reading star " + star.getName());
				}
			}

			nameNode = planetNode.getAttributes().getNamedItem("numPlanets");

			try {
				maxPlanetNumber.put(star ,Integer.parseInt(nameNode.getNodeValue()));
			} catch (Exception e) {
				AdvancedRocketry.logger.warn("Invalid number of planets specified in xml config!");
			}

			nameNode = planetNode.getAttributes().getNamedItem("numGasGiants");
			try {
				maxGasPlanetNumber.put(star ,Integer.parseInt(nameNode.getNodeValue()));
			} catch (Exception e) {
				AdvancedRocketry.logger.warn("Invalid number of planets specified in xml config!");
			}
		}

		star.setId(starId++);
		return star;
	}
	
	public StellarBody readSubStar(Node planetNode) {
		StellarBody star = new StellarBody();
		if(planetNode.hasAttributes()) {
			Node nameNode = planetNode.getAttributes().getNamedItem("name");
			if(nameNode != null && !nameNode.getNodeValue().isEmpty()) {
				star.setName(nameNode.getNodeValue());
			}

			nameNode = planetNode.getAttributes().getNamedItem("temp");

			if(nameNode != null && !nameNode.getNodeValue().isEmpty()) {
				try {
					star.setTemperature(Integer.parseInt(nameNode.getNodeValue()));
				} catch (NumberFormatException e) {
					AdvancedRocketry.logger.warn("Error Reading star " + star.getName());
				}
			}
			
			nameNode = planetNode.getAttributes().getNamedItem("size");
			if(nameNode != null && !nameNode.getNodeValue().isEmpty()) {
				try {
					star.setSize(Float.parseFloat(nameNode.getNodeValue()));
				} catch (NumberFormatException e) {
					AdvancedRocketry.logger.warn("Error Reading star " + star.getName());
				}
			}
			
			nameNode = planetNode.getAttributes().getNamedItem("seperation");
			if(nameNode == null)
				nameNode = planetNode.getAttributes()
						.getNamedItem("separation");
			if(nameNode != null && !nameNode.getNodeValue().isEmpty()) {
				try {
					star.setStarSeperation(Float.parseFloat(nameNode.getNodeValue()));
				} catch (NumberFormatException e) {
					AdvancedRocketry.logger.warn("Error Reading star " + star.getName());
				}
			}
		}

		readBlackHoleAttributes(planetNode, star);
		
		return star;
	}

	public DimensionPropertyCoupling readAllPlanets() {
		DimensionPropertyCoupling coupling = new DimensionPropertyCoupling();

		Node masterNode = doc.getElementsByTagName("galaxy").item(0).getFirstChild();

		//readPlanetFromNode changes value
		//Yes it's hacky but that's another reason why it's private

		offset = DimensionManager.dimOffset;
		while(masterNode != null) {
			if(!masterNode.getNodeName().equals("star")) {
				masterNode = masterNode.getNextSibling();
				continue;
			}

			StellarBody star = readStar(masterNode);
			coupling.stars.add(star);

			NodeList planetNodeList = masterNode.getChildNodes();

			Node planetNode = planetNodeList.item(0);

			while(planetNode != null) {
				if(planetNode.getNodeName().equalsIgnoreCase("planet")) {
					coupling.dims.addAll(readPlanetFromNode(planetNode, star));
				}
				if(planetNode.getNodeName().equalsIgnoreCase("star")) {
					StellarBody star2 = readSubStar(planetNode);
					star.addSubStar(star2);
				}
				planetNode = planetNode.getNextSibling();
			}

			masterNode = masterNode.getNextSibling();
		}
		return coupling;
	}

	public static String writeXML(IGalaxy galaxy) {
		//galaxy.
		String outputString = "<galaxy>\n";

		Collection<StellarBody> stars = galaxy.getStars();

		for(StellarBody star : stars) {
			outputString = outputString + "\t<star name=\""
					+ escapeXml(star.getName()) + "\" temp=\""
					+ star.getTemperature()
					+ "\" coordinateSchema=\"xyz\" x=\"" + star.getPosX()
					+ "\" y=\"" + star.getPosY() + "\" z=\""
					+ star.getPosZ() + "\" size=\"" + star.getSize()
					+ "\" numPlanets=\"0\" numGasGiants=\"0\""
					+ writeBlackHoleAttributes(star) + ">\n";

			for(StellarBody star2 : star.getSubStars()) {
				outputString = outputString + "\t\t<star temp=\""
						+ star2.getTemperature() + "\" size=\""
						+ star2.getSize() + "\" seperation=\""
						+ star2.getStarSeperation() + "\""
						+ writeBlackHoleAttributes(star2) + " />\n";

			}
			
			for(IDimensionProperties properties : star.getPlanets()) {
				if(!properties.isMoon())
					outputString = outputString + writePlanet((DimensionProperties)properties, 2);
			}

			outputString = outputString + "\t</star>\n";
		}

		outputString = outputString + "</galaxy>";

		return outputString;
	}

	private static String writePlanet(DimensionProperties properties, int numTabs) {
		String outputString = "";
		String tabLen = "";

		for(int i = 0; i < numTabs; i++) {
			tabLen += "\t";
		}

		outputString = tabLen + "<planet name=\""
				+ escapeXml(properties.getName()) + "\" DIMID=\""
				+ properties.getId() + "\"" +
				(properties.isNativeDimension ? "" : " dimMapping=\"\"") + 
				(properties.customIcon.isEmpty() ? "" : " customIcon=\""
						+ escapeXml(properties.customIcon) + "\"") + ">\n";


		outputString = outputString + tabLen + "\t<isKnown>" + Configuration.initiallyKnownPlanets.contains(properties.getId()) + "</isKnown>\n";	
		if(properties.hasRings) {
			outputString = outputString + tabLen + "\t<hasRings>true</hasRings>\n";
			outputString = outputString + tabLen + "\t<ringColor>" + properties.ringColor[0] + "," + properties.ringColor[1] + "," + properties.ringColor[2] + "</ringColor>\n";
		}

		if(!properties.hasOxygen)
		{
			outputString = outputString + tabLen + "\t<hasOxygen>false</hasOxygen>\n";
		}

		if(properties.isGasGiant())
		{
			outputString = outputString + tabLen + "\t<GasGiant>true</GasGiant>\n";
			if(!properties.getHarvestableGasses().isEmpty())
			{
				for(Fluid f : properties.getHarvestableGasses())
				{
					outputString = outputString + tabLen + "\t<gas>" + f.getName() + "</gas>\n";
				}
				
			}
		}

		outputString = outputString + tabLen + "\t<fogColor>" + properties.fogColor[0] + "," + properties.fogColor[1] + "," + properties.fogColor[2] + "</fogColor>\n";
		outputString = outputString + tabLen + "\t<skyColor>" + properties.skyColor[0] + "," + properties.skyColor[1] + "," + properties.skyColor[2] + "</skyColor>\n";
		outputString = outputString + tabLen + "\t<gravitationalMultiplier>" + (int)(properties.getGravitationalMultiplier()*100f) + "</gravitationalMultiplier>\n";
		outputString = outputString + tabLen + "\t<orbitalDistance>" + properties.getOrbitalDist() + "</orbitalDistance>\n";
		outputString = outputString + tabLen + "\t<orbitalTheta>" + (int)(properties.baseOrbitTheta * 180d/Math.PI) + "</orbitalTheta>\n";
		outputString = outputString + tabLen + "\t<solarInsolationMult>" + properties.peakInsolationMultiplier + "</solarInsolationMult>\n";
		outputString = outputString + tabLen + "\t<avgTemperature>" + (int)(properties.averageTemperature) + "</avgTemperature>\n";
		outputString = outputString + tabLen + "\t<mass>" + properties.getMass() + "</mass>\n";
		outputString = outputString + tabLen + "\t<orbitalPhi>" + (int)(properties.orbitalPhi) + "</orbitalPhi>\n";
		outputString = outputString + tabLen + "\t<rotationalPeriod>" + (int)properties.rotationalPeriod + "</rotationalPeriod>\n";
		outputString = outputString + tabLen + "\t<atmosphereDensity>" + (int)properties.getAtmosphereDensity() + "</atmosphereDensity>\n";
		outputString = outputString + writeAtmosphereRendering(
				properties.getAtmosphereVisualProperties(),
				numTabs + 1);
		
		if(properties.getSeaLevel() != 63)
			outputString = outputString + tabLen + "\t<seaLevel>" + properties.getSeaLevel() + "</seaLevel>\n";
		
		if(properties.getGenType() != 0)
			outputString = outputString + tabLen + "\t<genType>" + properties.getGenType() + "</genType>\n";
		
		if(properties.oreProperties != null) {
			outputString = outputString + tabLen + "\t<oreGen>\n";
			outputString = outputString + XMLOreLoader.writeOreEntryXML(properties.oreProperties, numTabs+2);
			outputString = outputString + tabLen + "\t</oreGen>\n";
		}
		
		if(properties.isNativeDimension && !properties.isGasGiant()) {
			String biomeIds = "";
			for(BiomeEntry biome : properties.getBiomes()) {
				biomeIds = biomeIds + "," + biome.biome.biomeID;
			}
			if(!biomeIds.isEmpty())
				biomeIds = biomeIds.substring(1);
			else
				AdvancedRocketry.logger.warn("Dim " + properties.getId() + " has no biomes to save!");
			
			outputString = outputString + tabLen + "\t<biomeIds>" + biomeIds + "</biomeIds>\n";
		}

		for(ItemStack stack : properties.getRequiredArtifacts()) {
			outputString = outputString + tabLen + "\t<artifact>" + Item.itemRegistry.getNameForObject(stack.getItem()) + ";" + stack.getItemDamage() + ";" + stack.stackSize + "</artifact>\n";
		}

		for(SpawnListEntryNBT spawn : properties.getSpawnListEntries()) {
			String entityName = spawn.getEntityIdentifier();
			if(entityName == null || entityName.isEmpty()) {
				Object registeredName =
						EntityList.classToStringMapping.get(spawn.entityClass);
				entityName = registeredName instanceof String
						? (String)registeredName
						: spawn.entityClass.getName();
			}
			outputString = outputString + tabLen + "\t<spawnable weight=\""
					+ spawn.itemWeight + "\" groupMin=\"" + spawn.minGroupCount
					+ "\" groupMax=\"" + spawn.maxGroupCount + "\""
					+ (spawn.getNBTString().isEmpty()
							? ""
							: " nbt=\"" + escapeXml(spawn.getNBTString()) + "\"")
					+ ">" + escapeXml(entityName) + "</spawnable>\n";
		}
		
		for(Integer properties2 : properties.getChildPlanets()) {
			outputString = outputString + writePlanet(DimensionManager.getInstance().getDimensionProperties(properties2), numTabs+1);
		}

		if(properties.getOceanBlock() != null) {
			outputString = outputString + tabLen + "\t<oceanBlock>" + Block.blockRegistry.getNameForObject(properties.getOceanBlock()) + "</oceanBlock>\n";
		}
		
		if(properties.getStoneBlock() != null) {
			outputString = outputString + tabLen + "\t<fillerBlock>" + Block.blockRegistry.getNameForObject(properties.getStoneBlock()) + "</fillerBlock>\n";
		}
		
		outputString = outputString + tabLen + "</planet>\n";
		return outputString;
	}

	private static String writeAtmosphereRendering(
			AtmosphereVisualProperties source, int numTabs) {
		AtmosphereVisualProperties properties =
				source == null ? new AtmosphereVisualProperties()
						: source.sanitizedCopy();
		if(!properties.hasOverrides())
			return "";

		StringBuilder tabBuilder = new StringBuilder();
		for(int index = 0; index < numTabs; index++)
			tabBuilder.append('\t');
		String tabs = tabBuilder.toString();
		String valueTabs = tabs + "\t";
		StringBuilder output = new StringBuilder();
		output.append(tabs)
				.append("<atmosphereRendering version=\"")
				.append(AtmosphereVisualProperties.SCHEMA_VERSION)
				.append("\">\n");

		if(properties.hasPlanetRadiusKm())
			appendAtmosphereTag(output, valueTabs, "planetRadiusKm",
					properties.getPlanetRadiusKm());
		if(properties.hasAtmosphereHeightKm())
			appendAtmosphereTag(output, valueTabs, "atmosphereHeightKm",
					properties.getAtmosphereHeightKm());
		if(properties.hasRayleighColor())
			appendAtmosphereColor(output, valueTabs, "rayleighColor",
					properties.getRayleighColorComponent(0),
					properties.getRayleighColorComponent(1),
					properties.getRayleighColorComponent(2));
		if(properties.hasRayleighStrength())
			appendAtmosphereTag(output, valueTabs, "rayleighStrength",
					properties.getRayleighStrength());
		if(properties.hasRayleighScaleHeightKm())
			appendAtmosphereTag(
					output, valueTabs, "rayleighScaleHeightKm",
					properties.getRayleighScaleHeightKm());
		if(properties.hasMieColor())
			appendAtmosphereColor(output, valueTabs, "mieColor",
					properties.getMieColorComponent(0),
					properties.getMieColorComponent(1),
					properties.getMieColorComponent(2));
		if(properties.hasMieStrength())
			appendAtmosphereTag(output, valueTabs, "mieStrength",
					properties.getMieStrength());
		if(properties.hasMieScaleHeightKm())
			appendAtmosphereTag(output, valueTabs, "mieScaleHeightKm",
					properties.getMieScaleHeightKm());
		if(properties.hasMieAnisotropy())
			appendAtmosphereTag(output, valueTabs, "mieAnisotropy",
					properties.getMieAnisotropy());
		if(properties.hasAbsorptionColor())
			appendAtmosphereColor(output, valueTabs, "absorptionColor",
					properties.getAbsorptionColorComponent(0),
					properties.getAbsorptionColorComponent(1),
					properties.getAbsorptionColorComponent(2));
		if(properties.hasAbsorptionStrength())
			appendAtmosphereTag(output, valueTabs, "absorptionStrength",
					properties.getAbsorptionStrength());
		if(properties.hasAbsorptionCenterKm())
			appendAtmosphereTag(output, valueTabs, "absorptionCenterKm",
					properties.getAbsorptionCenterKm());
		if(properties.hasAbsorptionWidthKm())
			appendAtmosphereTag(output, valueTabs, "absorptionWidthKm",
					properties.getAbsorptionWidthKm());
		if(properties.hasMultipleScatteringStrength())
			appendAtmosphereTag(
					output, valueTabs, "multipleScatteringStrength",
					properties.getMultipleScatteringStrength());
		if(properties.hasSunIntensityMultiplier())
			appendAtmosphereTag(
					output, valueTabs, "sunIntensityMultiplier",
					properties.getSunIntensityMultiplier());
		if(properties.hasExposure())
			appendAtmosphereTag(output, valueTabs, "exposure",
					properties.getExposure());
		if(properties.hasCloudLayerMode()) {
			output.append(valueTabs)
					.append("<cloudLayerMode>")
					.append(properties.getCloudLayerMode().name())
					.append("</cloudLayerMode>\n");
		}

		output.append(tabs).append("</atmosphereRendering>\n");
		return output.toString();
	}

	private static void appendAtmosphereTag(
			StringBuilder output, String tabs,
			String tag, double value) {
		output.append(tabs).append('<').append(tag).append('>')
				.append(value)
				.append("</").append(tag).append(">\n");
	}

	private static void appendAtmosphereColor(
			StringBuilder output, String tabs, String tag,
			float red, float green, float blue) {
		output.append(tabs).append('<').append(tag).append('>')
				.append(red).append(',')
				.append(green).append(',')
				.append(blue)
				.append("</").append(tag).append(">\n");
	}

	private static String escapeXml(String value) {
		if(value == null)
			return "";
		return value.replace("&", "&amp;")
				.replace("\"", "&quot;")
				.replace("<", "&lt;")
				.replace(">", "&gt;")
				.replace("'", "&apos;");
	}

	private static void readBlackHoleAttributes(Node starNode,
			StellarBody star) {
		if(!starNode.hasAttributes())
			return;
		Node blackHoleNode = starNode.getAttributes().getNamedItem("blackHole");
		if(blackHoleNode == null)
			return;

		String blackHoleValue = blackHoleNode.getNodeValue();
		if(!"true".equalsIgnoreCase(blackHoleValue)
				&& !"false".equalsIgnoreCase(blackHoleValue)) {
			AdvancedRocketry.logger.warn("Invalid blackHole value '"
					+ blackHoleValue + "' for star " + star.getName()
					+ "; using false");
			return;
		}

		star.setBlackHole(Boolean.parseBoolean(blackHoleValue));
		if(!star.isBlackHole())
			return;

		BlackHoleProperties properties =
				star.getOrCreateBlackHoleProperties();
		readBlackHoleDouble(starNode, star, "blackHoleMass",
				new BlackHoleValueSetter() {
					@Override
					public void set(BlackHoleProperties target, double value) {
						target.setMass(value);
					}
				});
		readBlackHoleDouble(starNode, star, "blackHoleSpin",
				new BlackHoleValueSetter() {
					@Override
					public void set(BlackHoleProperties target, double value) {
						target.setSpin(value);
					}
				});
		readBlackHoleDouble(starNode, star, "blackHoleAccretionRate",
				new BlackHoleValueSetter() {
					@Override
					public void set(BlackHoleProperties target, double value) {
						target.setAccretionRate(value);
					}
				});
		readBlackHoleDouble(starNode, star,
				"blackHoleAxisInclination", new BlackHoleValueSetter() {
					@Override
					public void set(BlackHoleProperties target, double value) {
						target.setSpinAxisInclinationDeg(value);
					}
				});
		readBlackHoleDouble(starNode, star, "blackHoleAxisYaw",
				new BlackHoleValueSetter() {
					@Override
					public void set(BlackHoleProperties target, double value) {
						target.setSpinAxisYawDeg(value);
					}
				});

		Node inner = starNode.getAttributes().getNamedItem(
				"blackHoleDiskInnerRadiusOverM");
		if(inner != null) {
			if("auto".equalsIgnoreCase(inner.getNodeValue()))
				properties.clearDiskInnerRadiusOverride();
			else
				readBlackHoleDouble(starNode, star,
						"blackHoleDiskInnerRadiusOverM",
						new BlackHoleValueSetter() {
							@Override
							public void set(BlackHoleProperties target,
									double value) {
								target.setDiskInnerRadiusOverM(value);
							}
						});
		}
		readBlackHoleDouble(starNode, star,
				"blackHoleDiskOuterRadiusOverM",
				new BlackHoleValueSetter() {
					@Override
					public void set(BlackHoleProperties target, double value) {
						target.setDiskOuterRadiusOverM(value);
					}
				});
		readBlackHoleDouble(starNode, star, "blackHoleVisualScale",
				new BlackHoleValueSetter() {
					@Override
					public void set(BlackHoleProperties target, double value) {
						target.setVisualScale(value);
					}
				});
		readBlackHoleDouble(starNode, star, "blackHoleCaptureRadius",
				new BlackHoleValueSetter() {
					@Override
					public void set(BlackHoleProperties target, double value) {
						target.setCaptureRadius(value);
					}
				});
		if(starNode.getAttributes().getNamedItem(
				"blackHoleInfluenceRadius") == null) {
			double capture = properties.getCaptureRadius();
			properties.setInfluenceRadius(capture
					> Double.MAX_VALUE / 16D
					? Double.MAX_VALUE : capture * 16D);
		}
		readBlackHoleDouble(starNode, star, "blackHoleInfluenceRadius",
				new BlackHoleValueSetter() {
					@Override
					public void set(BlackHoleProperties target, double value) {
						target.setInfluenceRadius(value);
					}
				});
		if(starNode.getAttributes().getNamedItem(
				"blackHoleWarningRadius") == null) {
			double influence = properties.getInfluenceRadius();
			properties.setWarningRadius(influence
					> Double.MAX_VALUE / 1.25D
					? Double.MAX_VALUE : influence * 1.25D);
		}
		readBlackHoleDouble(starNode, star, "blackHoleWarningRadius",
				new BlackHoleValueSetter() {
					@Override
					public void set(BlackHoleProperties target, double value) {
						target.setWarningRadius(value);
					}
				});
	}

	private static void readBlackHoleDouble(Node starNode, StellarBody star,
			String attribute, BlackHoleValueSetter setter) {
		Node valueNode = starNode.getAttributes().getNamedItem(attribute);
		if(valueNode == null)
			return;
		try {
			double value = Double.parseDouble(valueNode.getNodeValue());
			setter.set(star.getOrCreateBlackHoleProperties(), value);
		}
		catch(NumberFormatException exception) {
			AdvancedRocketry.logger.warn("Invalid " + attribute + " value '"
					+ valueNode.getNodeValue() + "' for star "
					+ star.getName() + "; using fallback");
		}
		catch(IllegalArgumentException exception) {
			AdvancedRocketry.logger.warn("Invalid " + attribute + " value '"
					+ valueNode.getNodeValue() + "' for star "
					+ star.getName() + "; using fallback");
		}
	}

	private static String writeBlackHoleAttributes(StellarBody star) {
		if(!star.isBlackHole())
			return " blackHole=\"false\"";
		BlackHoleProperties properties =
				star.getOrCreateBlackHoleProperties();
		StringBuilder builder = new StringBuilder(" blackHole=\"true\"");
		builder.append(" blackHoleMass=\"").append(properties.getMass())
				.append('"');
		builder.append(" blackHoleSpin=\"").append(properties.getSpin())
				.append('"');
		builder.append(" blackHoleAccretionRate=\"")
				.append(properties.getAccretionRate()).append('"');
		builder.append(" blackHoleAxisInclination=\"")
				.append(properties.getSpinAxisInclinationDeg()).append('"');
		builder.append(" blackHoleAxisYaw=\"")
				.append(properties.getSpinAxisYawDeg()).append('"');
		builder.append(" blackHoleDiskInnerRadiusOverM=\"");
		if(properties.hasDiskInnerRadiusOverride())
			builder.append(properties.getDiskInnerRadiusOverM());
		else
			builder.append("auto");
		builder.append('"');
		builder.append(" blackHoleDiskOuterRadiusOverM=\"")
				.append(properties.getDiskOuterRadiusOverM()).append('"');
		builder.append(" blackHoleVisualScale=\"")
				.append(properties.getVisualScale()).append('"');
		builder.append(" blackHoleCaptureRadius=\"")
				.append(properties.getCaptureRadius()).append('"');
		builder.append(" blackHoleInfluenceRadius=\"")
				.append(properties.getInfluenceRadius()).append('"');
		builder.append(" blackHoleWarningRadius=\"")
				.append(properties.getWarningRadius()).append('"');
		return builder.toString();
	}

	private interface BlackHoleValueSetter {
		void set(BlackHoleProperties target, double value);
	}

	public static class DimensionPropertyCoupling {

		public List<StellarBody> stars = new LinkedList<StellarBody>();
		public List<DimensionProperties> dims = new LinkedList<DimensionProperties>();


	}

	
	public static ItemStack getStack(String text) {
		String trimmedText = text == null ? "" : text.trim();
		String splitStr[] = trimmedText.indexOf(';') >= 0
				? trimmedText.split("\\s*;\\s*")
				: trimmedText.split("\\s+");
		int meta = 0;
		int size = 1;
		// Preferred format: "name;meta;size"; whitespace remains accepted for old XML files.
		if(splitStr.length > 1) {
			try {
				meta = Integer.parseInt(splitStr[1]);
			} catch( NumberFormatException e) {}
			
			if(splitStr.length > 2)
			{
				try {
					size = Integer.parseInt(splitStr[2]);
				} catch( NumberFormatException e) {}
			}
		}

		ItemStack stack = null;
		Block block = Block.getBlockFromName(splitStr[0]);
		if(block == null) {

			//Try getting item by name first
			Item item = (Item) Item.itemRegistry.getObject(splitStr[0]);

			if(item != null)
				stack = new ItemStack(item, size, meta);
			else {
				try {

					item = Item.getItemById(Integer.parseInt(splitStr[0]));
					if(item != null)
						stack = new ItemStack(item, size, meta);
				} catch (NumberFormatException e) { return null;}

			}
		}
		else
			stack = new ItemStack(block, size, meta);
	
		return stack;
	}
}
