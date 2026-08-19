# Track C — Practice & Scrimmage Recording System

**Purpose:** Record flat + VR gameplay footage for film review  
**Status:** Off-robot only (wireless allowed)  
**Priority:** Lower than Track B (complete Tracks A & B first)

---

## Overview

Track C is a completely separate system from the competition robot. It uses a Quest headset (either a second unit or the same one between matches) to record practice sessions and scrimmages for coaching and film study.

### Key Features

- **Dual Recording:** Simultaneously records:
  - Flat MP4 (what the operator sees on their screen/phone)
  - Stereo SBS VR footage (full immersive perspective)
  
- **Session Manifest:** JSON metadata file with:
  - Session ID
  - Timestamps
  - Camera IDs
  - Dropped frame counts
  
- **Review Dashboard:** Web interface for browsing and playing back sessions

### Legal Status

✅ **Fully legal** — Track C equipment is **never attached to or communicating with the ROBOT** during matches. FRC Rule R707 does not apply to sideline/recording equipment.

---

## Architecture

```
Quest Headset (handheld/tripod)
        │
   Wi-Fi (802.11ac recommended)
        │
   Bridge Laptop (pit PC / dedicated recorder)
        │
   ├── Flat Recorder (MJPEG/MP4)
   ├── VR Recorder (stereo SBS MP4)
   └── Session Manifest Writer (JSON)
        │
   External SSD (storage)
        │
   Review Dashboard (web UI)
```

---

## Hardware Requirements

| Component | Minimum | Recommended |
|-----------|---------|-------------|
| Quest Headset | Quest 2 | Quest 3S (same as Track B) |
| Bridge Laptop | i5, 8GB RAM, Gigabit Ethernet | i7/Ryzen 7, 16GB RAM, Wi-Fi 6E |
| Storage | 256GB SSD | 1TB+ NVMe SSD (external) |
| Network | Wi-Fi 5 (802.11ac) | Wi-Fi 6E (802.11ax) for lower latency |

### Notes

- **Wi-Fi is fine here** — This system is never part of the ROBOT during matches
- **Stereo video is large** — Budget ~500MB-1GB per minute for high-quality VR recording
- **Dedicated recorder preferred** — Don't run bridge on same PC as scouting software

---

## Software Components

### 1. Bridge Service

Runs on pit laptop, receives video stream from Quest.

#### Options:

**Option A: Oculus Debug Tool (Built-in)**
- Meta provides basic casting/recording tools
- Limited customization, no manifest generation
- Good for quick testing

**Option B: Custom Bridge (Recommended)**

Python-based service using Quest's casting protocol:

```python
# track-c-recorder/bridge_service.py
import asyncio
import websockets
import json
from datetime import datetime
from pathlib import Path

class BridgeService:
    def __init__(self, output_dir: Path):
        self.output_dir = output_dir
        self.session_id = None
        self.start_time = None
        self.frame_count = 0
        self.dropped_frames = 0
        
    async def start_session(self):
        """Initialize new recording session"""
        self.session_id = datetime.now().strftime("%Y%m%d_%H%M%S")
        self.start_time = datetime.now()
        self.frame_count = 0
        self.dropped_frames = 0
        
        # Create session directory
        session_dir = self.output_dir / self.session_id
        session_dir.mkdir(parents=True, exist_ok=True)
        
        # Initialize manifest
        self.manifest = {
            "session_id": self.session_id,
            "start_time": self.start_time.isoformat(),
            "cameras": [],
            "frame_count": 0,
            "dropped_frames": 0
        }
        
        print(f"Started session {self.session_id}")
        
    async def record_frame(self, frame_data, is_vr=True):
        """Record incoming frame"""
        self.frame_count += 1
        
        # Save frame to appropriate file
        if is_vr:
            # Write to VR (stereo SBS) file
            pass
        else:
            # Write to flat file
            pass
            
    async def end_session(self):
        """Finalize recording session"""
        end_time = datetime.now()
        
        # Finalize manifest
        self.manifest["end_time"] = end_time.isoformat()
        self.manifest["duration_seconds"] = (end_time - self.start_time).total_seconds()
        self.manifest["frame_count"] = self.frame_count
        self.manifest["dropped_frames"] = self.dropped_frames
        
        # Write manifest
        manifest_path = self.output_dir / self.session_id / "manifest.json"
        with open(manifest_path, 'w') as f:
            json.dump(self.manifest, f, indent=2)
            
        print(f"Ended session {self.session_id}")
        print(f"Duration: {self.manifest['duration_seconds']:.1f}s")
        print(f"Frames: {self.frame_count}, Dropped: {self.dropped_frames}")
```

### 2. Quest Casting App

Modified Unity app (or separate build) that streams video to bridge.

#### Implementation Options:

**Option A: Meta Cast Feature**
- Built into Quest OS
- Cast to Chromecast-compatible receiver
- Simple but limited control

**Option B: Custom UDP Streamer**
- Unity script sends frames via UDP
- Full control over resolution, framerate, codec
- More complex but better performance

```csharp
// Assets/Scripts/VideoStreamer.cs
using System.Net;
using System.Net.Sockets;
using UnityEngine;

public class VideoStreamer : MonoBehaviour
{
    private UdpClient udpClient;
    private IPEndPoint endPoint;
    
    [SerializeField] private string targetIP = "192.168.1.100";
    [SerializeField] private int port = 5000;
    [SerializeField] private int targetFPS = 60;
    
    void Start()
    {
        udpClient = new UdpClient();
        endPoint = new IPEndPoint(IPAddress.Parse(targetIP), port);
        
        Application.targetFrameRate = targetFPS;
        Screen.SetResolution(1920, 1080, false);
    }
    
    void OnRenderImage(RenderTexture source, RenderTexture destination)
    {
        // Capture frame
        Texture2D frame = new Texture2D(source.width, source.height);
        RenderTexture.active = source;
        frame.ReadPixels(new Rect(0, 0, source.width, source.height), 0, 0);
        frame.Apply();
        
        // Encode as JPEG
        byte[] jpgData = frame.EncodeToJPG(80);
        
        // Send via UDP
        udpClient.Send(jpgData, jpgData.Length, endPoint);
        
        RenderTexture.active = null;
        Graphics.Blit(source, destination);
    }
    
    void OnDestroy()
    {
        udpClient.Close();
    }
}
```

### 3. Review Dashboard

Web interface for browsing and playing back recorded sessions.

#### Tech Stack:
- **Frontend:** React or vanilla HTML/JS
- **Backend:** Python Flask or Node.js Express
- **Video Player:** Video.js with VR plugin (for stereo playback)

#### Features:
- Session list with thumbnails
- Filter by date, duration, tags
- Flat vs VR toggle
- Playback speed control
- Frame-by-frame scrubbing
- Export clips

---

## Setup Instructions

### Step 1: Prepare Quest Headset

1. Enable Developer Mode (same as Track B)
2. Install custom casting app (or use built-in cast feature)
3. Configure Wi-Fi connection (event network or direct AP)

### Step 2: Set Up Bridge Laptop

```bash
# Install dependencies
pip install asyncio websockets opencv-python numpy

# Create output directory
mkdir -p /data/track-c-recordings

# Start bridge service
cd /workspace/track-c-recorder
python bridge_service.py --output-dir /data/track-c-recordings
```

### Step 3: Test Connection

1. Put on Quest headset
2. Start casting/streaming from Quest app
3. Verify bridge receives frames
4. Check output files are created

### Step 4: Record Test Session

1. Click "Start Recording" in bridge UI (or auto-start on connection)
2. Move around with headset for 30 seconds
3. Stop recording
4. Verify both flat and VR files saved correctly
5. Check manifest JSON for accuracy

---

## Usage at Practice/Scrimmage

### Pre-Session

1. Power up bridge laptop
2. Start bridge service
3. Charge Quest headset
4. Mount Quest on tripod or hand to operator

### During Session

1. Start recording before match begins
2. Operator films action (handheld or fixed position)
3. Monitor dropped frames (should be <1% on good network)
4. Stop recording after match ends

### Post-Session

1. Review footage immediately if needed
2. Tag notable moments (optional)
3. Backup to external drive
4. Share with team for analysis

---

## File Organization

```
/data/track-c-recordings/
├── 20270115_143022/          # Session ID (timestamp)
│   ├── flat.mp4              # Flat recording
│   ├── vr_stereo.mp4         # VR stereo SBS recording
│   └── manifest.json         # Metadata
├── 20270115_144530/
│   ├── flat.mp4
│   ├── vr_stereo.mp4
│   └── manifest.json
└── ...
```

### Manifest Format

```json
{
  "session_id": "20270115_143022",
  "start_time": "2027-01-15T14:30:22.123Z",
  "end_time": "2027-01-15T14:32:45.456Z",
  "duration_seconds": 143.3,
  "cameras": [
    {
      "id": "quest_main",
      "resolution": "1920x1080",
      "fps": 60,
      "codec": "h264"
    }
  ],
  "frame_count": 8598,
  "dropped_frames": 12,
  "notes": "Practice scrimmage - blue alliance"
}
```

---

## Performance Optimization

### Reduce Latency

- Use Wi-Fi 6E (6 GHz band) for less interference
- Position laptop close to Quest (line of sight)
- Lower resolution (1280x720 instead of 1920x1080)
- Use UDP instead of TCP (acceptable frame loss)

### Improve Quality

- Increase bitrate (target 20-50 Mbps for VR)
- Use hardware encoding (NVENC on NVIDIA GPU)
- Record at native Quest resolution (up to 2064x2208 per eye)
- Store as lossless or lightly compressed format

### Manage Storage

- Estimate ~500MB/min for high-quality stereo
- 1TB SSD = ~33 hours of recording
- Implement automatic cleanup (delete oldest sessions)
- Compress old sessions overnight

---

## Integration with Track B

While Track C is independent, you can optionally:

1. **Use same Quest unit** between matches (swap between wired Track B mode and wireless Track C mode)
2. **Sync timestamps** with robot logs for correlated analysis
3. **Export QuestNav pose data** as overlay on recorded footage (advanced visualization)

---

## Future Enhancements

- [ ] Auto-tagging using computer vision (detect goals, notes, robots)
- [ ] Multi-camera sync (multiple Quests recording same match)
- [ ] Live streaming to cloud for remote viewing
- [ ] AR overlays (pose estimate, telemetry) on recorded footage
- [ ] Automated highlight reel generation

---

## Troubleshooting

| Issue | Cause | Solution |
|-------|-------|----------|
| High dropped frames | Weak Wi-Fi signal | Move closer, use 5/6 GHz band |
| Out of sync audio/video | Clock drift | Resync periodically, use PTS timestamps |
| Files too large | Excessive bitrate | Lower quality settings, use H.265 |
| Can't connect | Firewall blocking | Allow ports 5000-5010, disable Windows firewall |
| VR playback distorted | Wrong projection | Use proper equirectangular/spherical player |

---

## Resources

- **Meta Casting Docs:** https://developer.meta.com/docs/hardware/quest/
- **Video.js VR Plugin:** https://github.com/videojs/videojs-vr
- **FFmpeg Documentation:** https://ffmpeg.org/documentation.html

---

## Revision History

- **Revision 1** (Current): Initial design based on project plan
