package me.mss1r.smallshipsheraldry.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

final class SailTextureSources {
    private static final Map<ResourceLocation, Source> SOURCES = new LinkedHashMap<>(4, 0.75F, true);
    private static long generation;

    private SailTextureSources() {
    }

    static Source get(ResourceLocation location) throws IOException {
        Source cached = SOURCES.get(location);
        if (cached != null) return cached;
        var resource = Minecraft.getInstance().getResourceManager().getResourceOrThrow(location);
        boolean clamp = resource.metadata().getSection(TextureMetadataSection.SERIALIZER)
                .map(TextureMetadataSection::isClamp).orElse(false);
        NativeImage image;
        try (var input = resource.open()) {
            image = NativeImage.read(input);
        }
        if (SOURCES.size() >= 4) {
            var iterator = SOURCES.values().iterator();
            iterator.next().image().close();
            iterator.remove();
        }
        Source result = new Source(image, clamp);
        SOURCES.put(location, result);
        return result;
    }

    static long generation() {
        return generation;
    }

    static void clear() {
        SOURCES.values().forEach(source -> source.image().close());
        SOURCES.clear();
        generation++;
    }

    record Source(NativeImage image, boolean clamp) {
    }
}
