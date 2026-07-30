package zmaster587.advancedRocketry.client.render.atmosphere;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GLContext;

import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

/**
 * GLSL 1.20 program wrapper with failure latching for one resource cycle.
 */
final class AtmosphereShaderProgram {

	private final ResourceLocation vertexResource;
	private final ResourceLocation fragmentResource;
	private final String diagnosticName;
	private final Map<String, Integer> uniformLocations =
			new HashMap<String, Integer>();
	private ContextCapabilities contextIdentity;
	private int programId;
	private boolean failedThisResourceCycle;

	AtmosphereShaderProgram(ResourceLocation vertexResource,
			ResourceLocation fragmentResource, String diagnosticName) {
		this.vertexResource = vertexResource;
		this.fragmentResource = fragmentResource;
		this.diagnosticName = diagnosticName;
	}

	boolean ensureLoaded(IResourceManager resourceManager) {
		if(!refreshContextIdentity())
			return false;
		if(failedThisResourceCycle)
			return false;
		if(programId != 0) {
			try {
				if(GL20.glIsProgram(programId))
					return true;
			}
			catch(Throwable ignored) {
			}
			programId = 0;
			uniformLocations.clear();
		}

		int vertex = 0;
		int fragment = 0;
		int linkedProgram = 0;
		try {
			vertex = compile(resourceManager, vertexResource,
					GL20.GL_VERTEX_SHADER);
			if(vertex == 0)
				return fail();
			fragment = compile(resourceManager, fragmentResource,
					GL20.GL_FRAGMENT_SHADER);
			if(fragment == 0)
				return fail();

			linkedProgram = GL20.glCreateProgram();
			if(linkedProgram == 0)
				throw new IllegalStateException("glCreateProgram returned 0");
			GL20.glAttachShader(linkedProgram, vertex);
			GL20.glAttachShader(linkedProgram, fragment);
			GL20.glLinkProgram(linkedProgram);
			if(GL20.glGetProgrami(linkedProgram, GL20.GL_LINK_STATUS)
					== GL11.GL_FALSE) {
				String log = GL20.glGetProgramInfoLog(linkedProgram, 8192);
				AtmosphereDiagnostics.warnOnce(
						"shader-link-" + diagnosticName,
						"Could not link " + diagnosticName + ": " + log);
				return fail();
			}

			programId = linkedProgram;
			linkedProgram = 0;
			uniformLocations.clear();
			return true;
		}
		catch(Throwable throwable) {
			AtmosphereDiagnostics.warnOnce(
					"shader-load-" + diagnosticName,
					"Could not load " + diagnosticName
					+ "; using the continuous fallback.", throwable);
			return fail();
		}
		finally {
			if(linkedProgram != 0)
				GL20.glDeleteProgram(linkedProgram);
			if(vertex != 0)
				GL20.glDeleteShader(vertex);
			if(fragment != 0)
				GL20.glDeleteShader(fragment);
		}
	}

	private boolean fail() {
		failedThisResourceCycle = true;
		return false;
	}

	private int compile(IResourceManager resourceManager,
			ResourceLocation resourceLocation, int shaderType)
			throws IOException {
		String source = expandIncludes(resourceManager, resourceLocation,
				readResource(resourceManager, resourceLocation), 0);
		int shader = GL20.glCreateShader(shaderType);
		if(shader == 0)
			throw new IllegalStateException("glCreateShader returned 0 for "
					+ resourceLocation);
		boolean compiled = false;
		try {
			GL20.glShaderSource(shader, source);
			GL20.glCompileShader(shader);
			if(GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS)
					== GL11.GL_FALSE) {
				String log = GL20.glGetShaderInfoLog(shader, 8192);
				AtmosphereDiagnostics.warnOnce(
						"shader-compile-" + resourceLocation,
						"Could not compile " + resourceLocation
						+ ": " + log);
				return 0;
			}
			compiled = true;
			return shader;
		}
		finally {
			if(!compiled) {
				try {
					GL20.glDeleteShader(shader);
				}
				catch(Throwable throwable) {
					AtmosphereDiagnostics.warnOnce(
							"shader-delete-" + resourceLocation,
							"Could not delete failed atmosphere shader "
							+ resourceLocation + ".", throwable);
				}
			}
		}
	}

	private static String readResource(IResourceManager resourceManager,
			ResourceLocation resourceLocation) throws IOException {
		IResource resource = resourceManager.getResource(resourceLocation);
		InputStream input = resource.getInputStream();
		try {
			BufferedReader reader = new BufferedReader(new InputStreamReader(
					input, "UTF-8"));
			StringBuilder builder = new StringBuilder();
			String line;
			while((line = reader.readLine()) != null)
				builder.append(line).append('\n');
			return builder.toString();
		}
		finally {
			input.close();
		}
	}

	private static String expandIncludes(IResourceManager resourceManager,
			ResourceLocation parent, String source, int depth)
			throws IOException {
		if(depth > 4)
			throw new IOException("Atmosphere shader include depth exceeded at "
					+ parent);
		StringBuilder expanded = new StringBuilder();
		String[] lines = source.split("\\r?\\n", -1);
		for(String line : lines) {
			String trimmed = line.trim();
			if(trimmed.startsWith("#include \"")
					&& trimmed.endsWith("\"")) {
				String includeName = trimmed.substring(
						"#include \"".length(), trimmed.length() - 1);
				String parentPath = parent.getResourcePath();
				int slash = parentPath.lastIndexOf('/');
				String includePath = slash < 0 ? includeName
						: parentPath.substring(0, slash + 1) + includeName;
				ResourceLocation include = new ResourceLocation(
						parent.getResourceDomain(), includePath);
				expanded.append(expandIncludes(resourceManager, include,
						readResource(resourceManager, include), depth + 1));
			}
			else {
				expanded.append(line).append('\n');
			}
		}
		return expanded.toString();
	}

	void use() {
		GL20.glUseProgram(programId);
	}

	void stop() {
		GL20.glUseProgram(0);
	}

	private int uniform(String name) {
		Integer cached = uniformLocations.get(name);
		if(cached != null)
			return cached.intValue();
		int location = GL20.glGetUniformLocation(programId, name);
		uniformLocations.put(name, Integer.valueOf(location));
		return location;
	}

	void set1i(String name, int value) {
		int location = uniform(name);
		if(location >= 0)
			GL20.glUniform1i(location, value);
	}

	void set1f(String name, float value) {
		int location = uniform(name);
		if(location >= 0)
			GL20.glUniform1f(location, value);
	}

	void set2f(String name, float first, float second) {
		int location = uniform(name);
		if(location >= 0)
			GL20.glUniform2f(location, first, second);
	}

	void set3f(String name, float first, float second, float third) {
		int location = uniform(name);
		if(location >= 0)
			GL20.glUniform3f(location, first, second, third);
	}

	void set4f(String name, float first, float second, float third,
			float fourth) {
		int location = uniform(name);
		if(location >= 0)
			GL20.glUniform4f(location, first, second, third, fourth);
	}

	void dispose() {
		boolean sameContext = false;
		if(programId != 0 && Display.isCreated()) {
			try {
				sameContext = contextIdentity != null
						&& GLContext.getCapabilities() == contextIdentity;
				if(sameContext && GL20.glIsProgram(programId))
					GL20.glDeleteProgram(programId);
			}
			catch(Throwable throwable) {
				AtmosphereDiagnostics.warnOnce(
						"shader-dispose-" + diagnosticName,
						"Could not dispose " + diagnosticName + ".",
						throwable);
			}
		}
		programId = 0;
		uniformLocations.clear();
		failedThisResourceCycle = false;
		contextIdentity = null;
	}

	void abandon() {
		programId = 0;
		uniformLocations.clear();
		failedThisResourceCycle = false;
		contextIdentity = null;
	}

	private boolean refreshContextIdentity() {
		if(!Display.isCreated())
			return false;
		try {
			ContextCapabilities current = GLContext.getCapabilities();
			if(contextIdentity == current)
				return true;

			// A numeric program id belongs to its original context.  The same
			// integer may already identify an unrelated program after context
			// recreation, so abandon it without querying or deleting it.
			programId = 0;
			uniformLocations.clear();
			failedThisResourceCycle = false;
			contextIdentity = current;
			return true;
		}
		catch(Throwable throwable) {
			AtmosphereDiagnostics.warnOnce(
					"shader-context-" + diagnosticName,
					"Could not identify the current GL context for "
							+ diagnosticName
							+ "; using the continuous fallback.",
					throwable);
			return false;
		}
	}
}
