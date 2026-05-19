package parcelpanic.media;

import parcelpanic.media.AssetKeys.AudioKey;

public record SoundEvent(AudioKey key, long enqueuedTime) {}
