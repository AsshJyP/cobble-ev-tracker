package io.github.asshjyp.cobbleevtracker.platform;

import java.nio.file.Path;
import java.util.Optional;

/** Lo único que EV Tracker necesita del loader. */
public interface IPlatformHelper {
	/** "Fabric" o "NeoForge" (solo para el log). */
	String getPlatformName();

	/** La carpeta config/ de la instancia. */
	Path getConfigDir();

	/** ¿Está cargado el mod con este id? */
	boolean isModLoaded(String modId);

	/**
	 * Una carpeta o archivo dentro del jar de otro mod (por ejemplo "data/cobblemon/species" en el de Cobblemon),
	 * como Path que se puede recorrer con Files.walk. Vacío si el mod no está o no tiene esa ruta.
	 */
	Optional<Path> findModResource(String modId, String path);
}
