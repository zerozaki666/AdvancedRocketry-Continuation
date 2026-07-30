package zmaster587.advancedRocketry.client.render.blackhole;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

/**
 * Small GLSL 1.20 program wrapper.  Shader errors are reported once and are
 * represented as an unavailable program, never as a fatal client error.
 */
final class BlackHoleShaderProgram {

	private final ResourceLocation vertexResource;
	private final ResourceLocation fragmentResource;
	private final String diagnosticName;
	private final Map<String, Integer> uniformLocations =
			new HashMap<String, Integer>();
	private int programId;

	BlackHoleShaderProgram(ResourceLocation vertexResource,
			ResourceLocation fragmentResource, String diagnosticName) {
		this.vertexResource = vertexResource;
		this.fragmentResource = fragmentResource;
		this.diagnosticName = diagnosticName;
	}

	boolean ensureLoaded(IResourceManager resourceManager) {
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
				return false;
			fragment = compile(resourceManager, fragmentResource,
					GL20.GL_FRAGMENT_SHADER);
			if(fragment == 0)
				return false;

			linkedProgram = GL20.glCreateProgram();
			if(linkedProgram == 0)
				throw new IllegalStateException("glCreateProgram returned 0");
			GL20.glAttachShader(linkedProgram, vertex);
			GL20.glAttachShader(linkedProgram, fragment);
			GL20.glLinkProgram(linkedProgram);
			if(GL20.glGetProgrami(linkedProgram, GL20.GL_LINK_STATUS)
					== GL11.GL_FALSE) {
				String log = GL20.glGetProgramInfoLog(linkedProgram, 8192);
				BlackHoleDiagnostics.warnOnce("shader-link-" + diagnosticName,
						"Could not link " + diagnosticName + " shader: " + log);
				return false;
			}

			programId = linkedProgram;
			linkedProgram = 0;
			uniformLocations.clear();
			return true;
		}
		catch(Throwable throwable) {
			BlackHoleDiagnostics.warnOnce("shader-load-" + diagnosticName,
					"Could not load " + diagnosticName
					+ " shader; using legacy visuals.", throwable);
			return false;
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

	private int compile(IResourceManager resourceManager,
			ResourceLocation resourceLocation, int shaderType)
			throws IOException {
		String source = readResource(resourceManager, resourceLocation);
		int shader = GL20.glCreateShader(shaderType);
		if(shader == 0)
			throw new IllegalStateException("glCreateShader returned 0 for "
					+ resourceLocation);
		GL20.glShaderSource(shader, source);
		GL20.glCompileShader(shader);
		if(GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS)
				== GL11.GL_FALSE) {
			String log = GL20.glGetShaderInfoLog(shader, 8192);
			BlackHoleDiagnostics.warnOnce("shader-compile-" + resourceLocation,
					"Could not compile " + resourceLocation + ": " + log);
			GL20.glDeleteShader(shader);
			return 0;
		}
		return shader;
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

	void use() {
		GL20.glUseProgram(programId);
	}

	void stop() {
		GL20.glUseProgram(0);
	}

	int getProgramId() {
		return programId;
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

	void set4f(String name, float first, float second, float third,
			float fourth) {
		int location = uniform(name);
		if(location >= 0)
			GL20.glUniform4f(location, first, second, third, fourth);
	}

	void dispose() {
		if(programId == 0)
			return;
		try {
			if(GL20.glIsProgram(programId))
				GL20.glDeleteProgram(programId);
		}
		catch(Throwable throwable) {
			BlackHoleDiagnostics.warnOnce(
					"shader-dispose-" + diagnosticName,
					"Could not dispose " + diagnosticName + " shader.",
					throwable);
		}
		finally {
			programId = 0;
			uniformLocations.clear();
		}
	}

	void abandon() {
		programId = 0;
		uniformLocations.clear();
	}
}
