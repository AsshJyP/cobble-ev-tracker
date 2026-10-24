package io.github.asshjyp.cobbleevtracker.platform;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforgespi.language.IModFileInfo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class NeoForgePlatformHelper implements IPlatformHelper {
	@Override
	public String getPlatformName() {
		return "NeoForge";
	}

	@Override
	public Path getConfigDir() {
		return FMLPaths.CONFIGDIR.get();
	}

	@Override
	public boolean isModLoaded(String modId) {
		return ModList.get().isLoaded(modId);
	}

	@Override
	public Optional<Path> findModResource(String modId, String path) {
		IModFileInfo info = ModList.get().getModFileById(modId);
		if (info == null) {
			return Optional.empty();
		}
		Path resource = info.getFile().findResource(path.split("/"));
		return Files.exists(resource) ? Optional.of(resource) : Optional.empty();
	}
}
