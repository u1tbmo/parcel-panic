package parcelpanic.media;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;
import parcelpanic.media.AssetKeys.AudioKey;

/// Service for playing audio
public final class AudioService {
  /// A cache of media players for each sound
  private final Map<AudioKey, MediaPlayer> soundCache = new HashMap<>();

  /// A queue of sounds to be played
  private final Queue<SoundEvent> soundQueue = new ArrayDeque<>();

  /// Currently playing music track (if any)
  private MediaPlayer musicPlayer;

  /// The master volume
  private double masterVolume = 0.5;

  /// Whether the game is muted
  private boolean muted = false;

  /// Play a sound effect immediately
  public void playSound(AudioKey key) {
    soundQueue.offer(new SoundEvent(key, System.currentTimeMillis()));
  }

  /// Process queued sounds each frame
  public void update() {
    // Don't add sounds to the queue if muted
    if (muted) {
      soundQueue.clear();
      return;
    }

    // Play all sounds in the queue if there are sounds to play
    while (!soundQueue.isEmpty()) {
      SoundEvent event = soundQueue.poll();
      playImmediately(event.key());
    }
  }

  /// Internal method to play the sound
  private void playImmediately(AudioKey key) {
    try {
      MediaPlayer player =
          soundCache.computeIfAbsent(
              key,
              k -> {
                try {
                  String resourcePath = "/sounds/" + k.getFileName() + ".mp3";
                  var resource = getClass().getResource(resourcePath);
                  if (resource == null) {
                    System.err.println("Sound resource not found: " + resourcePath);
                    return null;
                  }
                  Media media = new Media(resource.toExternalForm());
                  return new MediaPlayer(media);
                } catch (Exception e) {
                  System.err.println("Failed to load sound: " + k.name());
                  return null;
                }
              });

      if (player != null) {
        player.seek(Duration.ZERO);
        player.setVolume(masterVolume);
        player.play();
      }
    } catch (Exception e) {
      System.err.println("Error playing sound: " + key.name() + " - " + e.getMessage());
    }
  }

  /// Set master volume
  public void setVolume(double volume) {
    this.masterVolume = Math.max(0.0, Math.min(1.0, volume));
    soundCache.values().stream()
        .filter(player -> player.getStatus() == MediaPlayer.Status.PLAYING)
        .forEach(player -> player.setVolume(this.masterVolume));
  }

  /// Mute/unmute all audio
  public void setMuted(boolean muted) {
    this.muted = muted;
    if (muted) {
      soundCache.values().forEach(MediaPlayer::stop);
      soundQueue.clear();
    }
  }

  public boolean isMuted() {
    return muted;
  }

  public double getVolume() {
    return masterVolume;
  }

  /// Play a music track on loop
  public void playMusic(AudioKey key) {
    if (muted) return;

    try {
      if (musicPlayer != null) {
        musicPlayer.stop();
      }

      musicPlayer =
          soundCache.computeIfAbsent(
              key,
              k -> {
                try {
                  String resourcePath = "/sounds/" + k.getFileName() + ".mp3";
                  var resource = getClass().getResource(resourcePath);
                  if (resource == null) {
                    System.err.println("Music resource not found: " + resourcePath);
                    return null;
                  }
                  Media media = new Media(resource.toExternalForm());
                  MediaPlayer player = new MediaPlayer(media);
                  player.setCycleCount(MediaPlayer.INDEFINITE);
                  return player;
                } catch (Exception e) {
                  System.err.println("Failed to load music: " + k.name());
                  return null;
                }
              });

      if (musicPlayer != null) {
        musicPlayer.seek(Duration.ZERO);
        musicPlayer.setVolume(masterVolume * 0.7); // Slightly lower than SFX
        musicPlayer.play();
      }
    } catch (Exception e) {
      System.err.println("Error playing music: " + key.name() + " - " + e.getMessage());
    }
  }

  /// Stop music track
  public void stopMusic() {
    if (musicPlayer != null) {
      musicPlayer.stop();
    }
  }

  /// Stop all currently playing sounds
  public void stopAll() {
    soundCache.values().forEach(MediaPlayer::stop);
    soundQueue.clear();
  }
}
