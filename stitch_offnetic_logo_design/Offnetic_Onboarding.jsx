import { useState, useEffect, useRef } from "react";

const SCREENS = ["splash", "perm1", "perm2", "perm3", "identity", "profile", "main"];

const permSlides = [
  {
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" style={{ width: 44, height: 44 }}>
        <path d="M8.288 15.038a5.25 5.25 0 0 1 7.424 0M5.106 11.856c3.807-3.808 9.98-3.808 13.788 0M1.924 8.674c5.565-5.565 14.587-5.565 20.152 0M12.53 18.22l-.53.53-.53-.53a.75.75 0 0 1 1.06 0Z" strokeLinecap="round" strokeLinejoin="round"/>
      </svg>
    ),
    tag: "01 / 03",
    title: "Find people\nnearby",
    desc: "Offnetic uses Bluetooth and Wi-Fi to detect trusted contacts around you. Your location is never stored or shared — it never leaves your device.",
    perms: ["Bluetooth", "Nearby Wi-Fi", "Location"],
    btnLabel: "Allow Connectivity",
  },
  {
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" style={{ width: 44, height: 44 }}>
        <path d="M6.827 6.175A2.31 2.31 0 0 1 5.186 7.23c-.38.054-.757.112-1.134.175C2.999 7.58 2.25 8.507 2.25 9.574V18a2.25 2.25 0 0 0 2.25 2.25h15A2.25 2.25 0 0 0 21.75 18V9.574c0-1.067-.75-1.994-1.802-2.169a47.865 47.865 0 0 0-1.134-.175 2.31 2.31 0 0 1-1.64-1.055l-.822-1.316a2.192 2.192 0 0 0-1.736-1.039 48.776 48.776 0 0 0-5.232 0 2.192 2.192 0 0 0-1.736 1.039l-.821 1.316Z" strokeLinecap="round" strokeLinejoin="round"/>
        <path d="M16.5 12.75a4.5 4.5 0 1 1-9 0 4.5 4.5 0 0 1 9 0ZM18.75 10.5h.008v.008h-.008V10.5Z" strokeLinecap="round" strokeLinejoin="round"/>
      </svg>
    ),
    tag: "02 / 03",
    title: "Scan & speak\nfreely",
    desc: "Camera is used to scan QR codes when adding trusted contacts. Microphone powers end-to-end encrypted voice messages and calls.",
    perms: ["Camera", "Microphone"],
    btnLabel: "Allow Camera & Mic",
  },
  {
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" style={{ width: 44, height: 44 }}>
        <path d="M14.857 17.082a23.848 23.848 0 0 0 5.454-1.31A8.967 8.967 0 0 1 18 9.75V9A6 6 0 0 0 6 9v.75a8.967 8.967 0 0 1-2.312 6.022c1.733.64 3.56 1.085 5.455 1.31m5.714 0a24.255 24.255 0 0 1-5.714 0m5.714 0a3 3 0 1 1-5.714 0" strokeLinecap="round" strokeLinejoin="round"/>
      </svg>
    ),
    tag: "03 / 03",
    title: "Stay in\nthe loop",
    desc: "Get pinged when a trusted contact is physically nearby. No background tracking — alerts fire only when someone you know is close.",
    perms: ["Notifications"],
    btnLabel: "Allow Notifications",
  },
];

// Noise SVG for texture
const NoiseBg = () => (
  <svg style={{ position: "absolute", inset: 0, width: "100%", height: "100%", opacity: 0.035, pointerEvents: "none", zIndex: 0 }}>
    <filter id="noise">
      <feTurbulence type="fractalNoise" baseFrequency="0.75" numOctaves="4" stitchTiles="stitch"/>
      <feColorMatrix type="saturate" values="0"/>
    </filter>
    <rect width="100%" height="100%" filter="url(#noise)"/>
  </svg>
);

const styles = {
  phone: {
    width: 390,
    height: 844,
    background: "#0A0A0A",
    borderRadius: 44,
    overflow: "hidden",
    position: "relative",
    boxShadow: "0 0 0 10px #1A1A1A, 0 40px 120px rgba(0,0,0,0.8), 0 0 0 11px #2a2a2a",
    fontFamily: "'Syne', sans-serif",
    display: "flex",
    flexDirection: "column",
  },
  statusBar: {
    display: "flex",
    justifyContent: "space-between",
    alignItems: "center",
    padding: "14px 28px 0",
    zIndex: 10,
    flexShrink: 0,
  },
  statusTime: { fontSize: 15, fontWeight: 600, color: "#fff", letterSpacing: 0.2 },
  statusIcons: { display: "flex", gap: 6, alignItems: "center" },
};

function SplashScreen({ onNext }) {
  const [visible, setVisible] = useState(false);
  const [ringScale, setRingScale] = useState(0.6);

  useEffect(() => {
    setTimeout(() => setVisible(true), 100);
    setTimeout(() => setRingScale(1), 200);
    setTimeout(() => onNext(), 2800);
  }, []);

  return (
    <div style={{ flex: 1, display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", position: "relative", overflow: "hidden" }}>
      <NoiseBg />
      {/* Ambient ring */}
      <div style={{
        position: "absolute",
        width: 320, height: 320,
        borderRadius: "50%",
        border: "1px solid rgba(255,255,255,0.06)",
        transform: `scale(${ringScale})`,
        transition: "transform 1.2s cubic-bezier(0.16,1,0.3,1)",
      }}/>
      <div style={{
        position: "absolute",
        width: 220, height: 220,
        borderRadius: "50%",
        border: "1px solid rgba(255,255,255,0.08)",
        transform: `scale(${ringScale})`,
        transition: "transform 1s cubic-bezier(0.16,1,0.3,1)",
      }}/>
      {/* Center glow */}
      <div style={{
        position: "absolute",
        width: 160, height: 160,
        borderRadius: "50%",
        background: "radial-gradient(circle, rgba(255,255,255,0.06) 0%, transparent 70%)",
      }}/>
      {/* Logo mark */}
      <div style={{
        width: 72, height: 72,
        background: "#fff",
        borderRadius: 22,
        display: "flex", alignItems: "center", justifyContent: "center",
        opacity: visible ? 1 : 0,
        transform: visible ? "translateY(0) scale(1)" : "translateY(12px) scale(0.9)",
        transition: "all 0.7s cubic-bezier(0.16,1,0.3,1)",
        zIndex: 1,
        marginBottom: 24,
      }}>
        <svg viewBox="0 0 32 32" style={{ width: 36, height: 36 }}>
          <circle cx="16" cy="16" r="4" fill="#0A0A0A"/>
          <circle cx="16" cy="16" r="9" fill="none" stroke="#0A0A0A" strokeWidth="2"/>
          <circle cx="16" cy="16" r="14" fill="none" stroke="#0A0A0A" strokeWidth="1.2" strokeDasharray="3 3"/>
        </svg>
      </div>
      <div style={{
        opacity: visible ? 1 : 0,
        transform: visible ? "translateY(0)" : "translateY(10px)",
        transition: "all 0.7s cubic-bezier(0.16,1,0.3,1) 0.15s",
        zIndex: 1, textAlign: "center",
      }}>
        <div style={{ fontSize: 28, fontWeight: 700, color: "#fff", letterSpacing: -0.5 }}>Offnetic</div>
        <div style={{ fontSize: 13, color: "rgba(255,255,255,0.35)", marginTop: 6, letterSpacing: 1.5, textTransform: "uppercase", fontWeight: 500 }}>Off-grid. Encrypted. Local.</div>
      </div>
    </div>
  );
}

function PermissionSlide({ slide, index, total, onNext }) {
  const [visible, setVisible] = useState(false);
  useEffect(() => { setTimeout(() => setVisible(true), 60); }, []);

  return (
    <div style={{ flex: 1, display: "flex", flexDirection: "column", position: "relative", overflow: "hidden" }}>
      <NoiseBg />
      {/* Top decorative line */}
      <div style={{ position: "absolute", top: 0, left: 28, right: 28, height: 1, background: "linear-gradient(90deg, transparent, rgba(255,255,255,0.08), transparent)" }}/>

      {/* Progress dots */}
      <div style={{ display: "flex", gap: 6, justifyContent: "center", paddingTop: 20, zIndex: 1 }}>
        {[0,1,2].map(i => (
          <div key={i} style={{
            width: i === index ? 20 : 6, height: 6,
            borderRadius: 3,
            background: i === index ? "#fff" : "rgba(255,255,255,0.18)",
            transition: "all 0.4s cubic-bezier(0.16,1,0.3,1)",
          }}/>
        ))}
      </div>

      <div style={{ flex: 1, display: "flex", flexDirection: "column", padding: "0 32px", justifyContent: "center", zIndex: 1 }}>
        {/* Tag */}
        <div style={{
          opacity: visible ? 1 : 0,
          transform: visible ? "translateY(0)" : "translateY(16px)",
          transition: "all 0.6s cubic-bezier(0.16,1,0.3,1)",
        }}>
          <span style={{ fontSize: 11, color: "rgba(255,255,255,0.3)", letterSpacing: 2.5, textTransform: "uppercase", fontWeight: 600 }}>{slide.tag}</span>
        </div>

        {/* Icon */}
        <div style={{
          marginTop: 28,
          color: "rgba(255,255,255,0.9)",
          opacity: visible ? 1 : 0,
          transform: visible ? "translateY(0)" : "translateY(16px)",
          transition: "all 0.6s cubic-bezier(0.16,1,0.3,1) 0.05s",
        }}>
          {slide.icon}
        </div>

        {/* Title */}
        <div style={{
          marginTop: 24,
          fontSize: 36, fontWeight: 700, color: "#fff",
          lineHeight: 1.15, letterSpacing: -1,
          whiteSpace: "pre-line",
          opacity: visible ? 1 : 0,
          transform: visible ? "translateY(0)" : "translateY(16px)",
          transition: "all 0.6s cubic-bezier(0.16,1,0.3,1) 0.1s",
        }}>
          {slide.title}
        </div>

        {/* Desc */}
        <div style={{
          marginTop: 16,
          fontSize: 15, color: "rgba(255,255,255,0.45)",
          lineHeight: 1.7, fontWeight: 400,
          opacity: visible ? 1 : 0,
          transform: visible ? "translateY(0)" : "translateY(16px)",
          transition: "all 0.6s cubic-bezier(0.16,1,0.3,1) 0.15s",
        }}>
          {slide.desc}
        </div>

        {/* Permission chips */}
        <div style={{
          display: "flex", gap: 8, flexWrap: "wrap", marginTop: 28,
          opacity: visible ? 1 : 0,
          transform: visible ? "translateY(0)" : "translateY(16px)",
          transition: "all 0.6s cubic-bezier(0.16,1,0.3,1) 0.2s",
        }}>
          {slide.perms.map(p => (
            <div key={p} style={{
              padding: "5px 12px",
              borderRadius: 20,
              border: "1px solid rgba(255,255,255,0.12)",
              fontSize: 12, color: "rgba(255,255,255,0.5)",
              fontWeight: 500, letterSpacing: 0.3,
            }}>{p}</div>
          ))}
        </div>
      </div>

      {/* Button */}
      <div style={{ padding: "0 32px 48px", zIndex: 1,
        opacity: visible ? 1 : 0,
        transform: visible ? "translateY(0)" : "translateY(16px)",
        transition: "all 0.6s cubic-bezier(0.16,1,0.3,1) 0.25s",
      }}>
        <button onClick={onNext} style={{
          width: "100%", padding: "17px 0",
          background: "#fff", border: "none",
          borderRadius: 16, cursor: "pointer",
          fontSize: 15, fontWeight: 700,
          color: "#0A0A0A", letterSpacing: -0.2,
          fontFamily: "'Syne', sans-serif",
          transition: "opacity 0.15s",
        }}
          onMouseDown={e => e.currentTarget.style.opacity = "0.85"}
          onMouseUp={e => e.currentTarget.style.opacity = "1"}
        >
          {slide.btnLabel}
        </button>
        <div style={{ textAlign: "center", marginTop: 14, fontSize: 12, color: "rgba(255,255,255,0.2)", letterSpacing: 0.2 }}>
          You can change this anytime in Settings
        </div>
      </div>
    </div>
  );
}

function IdentityScreen({ onNext }) {
  const [phase, setPhase] = useState(0); // 0=generating, 1=done
  const [progress, setProgress] = useState(0);
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    setTimeout(() => setVisible(true), 60);
    const interval = setInterval(() => {
      setProgress(p => {
        if (p >= 100) { clearInterval(interval); setTimeout(() => setPhase(1), 400); return 100; }
        return p + 2;
      });
    }, 40);
    return () => clearInterval(interval);
  }, []);

  const lines = [
    "Generating ECDH P-256 keypair",
    "Deriving public identity",
    "Initialising PQXDH bundle",
    "Sealing into Keystore",
    "Identity secured",
  ];
  const activeLine = Math.floor((progress / 100) * lines.length);

  return (
    <div style={{ flex: 1, display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", position: "relative", padding: "0 32px" }}>
      <NoiseBg />

      {/* Orbital animation */}
      <div style={{ position: "relative", width: 140, height: 140, marginBottom: 48 }}>
        {/* Outer ring spinning */}
        <div style={{
          position: "absolute", inset: 0,
          border: "1px solid rgba(255,255,255,0.08)",
          borderRadius: "50%",
          borderTopColor: phase === 0 ? "rgba(255,255,255,0.5)" : "transparent",
          animation: phase === 0 ? "spin 1.2s linear infinite" : "none",
        }}/>
        {/* Middle ring */}
        <div style={{
          position: "absolute", inset: 18,
          border: "1px solid rgba(255,255,255,0.06)",
          borderRadius: "50%",
          borderBottomColor: phase === 0 ? "rgba(255,255,255,0.3)" : "transparent",
          animation: phase === 0 ? "spinR 1.8s linear infinite" : "none",
        }}/>
        {/* Center */}
        <div style={{
          position: "absolute", inset: 0,
          display: "flex", alignItems: "center", justifyContent: "center",
        }}>
          {phase === 0 ? (
            <div style={{ width: 32, height: 32, borderRadius: "50%", background: "rgba(255,255,255,0.1)", display: "flex", alignItems: "center", justifyContent: "center" }}>
              <div style={{ width: 8, height: 8, borderRadius: "50%", background: "#fff" }}/>
            </div>
          ) : (
            <div style={{ fontSize: 28 }}>✓</div>
          )}
        </div>
      </div>

      {/* Title */}
      <div style={{ fontSize: 24, fontWeight: 700, color: "#fff", letterSpacing: -0.5, marginBottom: 8, opacity: visible ? 1 : 0, transition: "opacity 0.5s", textAlign: "center" }}>
        {phase === 0 ? "Creating your identity" : "Identity created"}
      </div>
      <div style={{ fontSize: 14, color: "rgba(255,255,255,0.35)", marginBottom: 40, textAlign: "center", lineHeight: 1.6 }}>
        {phase === 0 ? "Your cryptographic identity is being\ngenerated entirely on this device." : "Your keys are sealed in the\nAndroid Keystore."}
      </div>

      {/* Log lines */}
      <div style={{ width: "100%", background: "rgba(255,255,255,0.03)", borderRadius: 12, padding: "14px 16px", border: "1px solid rgba(255,255,255,0.06)" }}>
        {lines.map((l, i) => (
          <div key={i} style={{
            display: "flex", alignItems: "center", gap: 10,
            padding: "5px 0",
            opacity: i <= activeLine ? 1 : 0.2,
            transition: "opacity 0.4s",
          }}>
            <div style={{ width: 6, height: 6, borderRadius: "50%", background: i < activeLine ? "#fff" : i === activeLine ? "#aaa" : "rgba(255,255,255,0.2)", flexShrink: 0 }}/>
            <div style={{ fontSize: 12, color: i <= activeLine ? "rgba(255,255,255,0.6)" : "rgba(255,255,255,0.2)", fontFamily: "monospace", letterSpacing: 0.3 }}>{l}</div>
          </div>
        ))}
      </div>

      {phase === 1 && (
        <button onClick={onNext} style={{
          marginTop: 32, width: "100%", padding: "17px 0",
          background: "#fff", border: "none", borderRadius: 16,
          cursor: "pointer", fontSize: 15, fontWeight: 700,
          color: "#0A0A0A", fontFamily: "'Syne', sans-serif",
          animation: "fadeUp 0.5s cubic-bezier(0.16,1,0.3,1) forwards",
        }}>
          Continue
        </button>
      )}

      <style>{`
        @keyframes spin { to { transform: rotate(360deg); } }
        @keyframes spinR { to { transform: rotate(-360deg); } }
        @keyframes fadeUp { from { opacity:0; transform:translateY(12px); } to { opacity:1; transform:translateY(0); } }
      `}</style>
    </div>
  );
}

function ProfileScreen({ onNext }) {
  const [username, setUsername] = useState("");
  const [visible, setVisible] = useState(false);
  const [avatarColor] = useState("#1E1E1E");

  useEffect(() => { setTimeout(() => setVisible(true), 60); }, []);

  const initials = username.slice(0, 2).toUpperCase() || "?";

  return (
    <div style={{ flex: 1, display: "flex", flexDirection: "column", position: "relative", padding: "0 32px", overflow: "hidden" }}>
      <NoiseBg />

      <div style={{ flex: 1, display: "flex", flexDirection: "column", justifyContent: "center" }}>
        <div style={{
          opacity: visible ? 1 : 0, transform: visible ? "translateY(0)" : "translateY(16px)",
          transition: "all 0.6s cubic-bezier(0.16,1,0.3,1)",
        }}>
          <div style={{ fontSize: 11, color: "rgba(255,255,255,0.3)", letterSpacing: 2.5, textTransform: "uppercase", fontWeight: 600, marginBottom: 24 }}>Set up profile</div>
          <div style={{ fontSize: 34, fontWeight: 700, color: "#fff", letterSpacing: -1, lineHeight: 1.2, marginBottom: 8 }}>How should\npeople see you?</div>
          <div style={{ fontSize: 15, color: "rgba(255,255,255,0.35)", lineHeight: 1.7, marginBottom: 40 }}>Your name and photo are only visible to trusted contacts you've paired with.</div>
        </div>

        {/* Avatar picker */}
        <div style={{
          display: "flex", alignItems: "center", gap: 20, marginBottom: 32,
          opacity: visible ? 1 : 0, transform: visible ? "translateY(0)" : "translateY(16px)",
          transition: "all 0.6s cubic-bezier(0.16,1,0.3,1) 0.1s",
        }}>
          <div style={{
            width: 80, height: 80, borderRadius: 26,
            background: avatarColor,
            border: "1px solid rgba(255,255,255,0.1)",
            display: "flex", alignItems: "center", justifyContent: "center",
            cursor: "pointer", position: "relative", overflow: "hidden",
          }}>
            <span style={{ fontSize: 24, fontWeight: 700, color: "rgba(255,255,255,0.6)" }}>{initials}</span>
            <div style={{
              position: "absolute", bottom: 0, left: 0, right: 0,
              background: "rgba(0,0,0,0.5)", padding: "5px 0",
              fontSize: 10, color: "rgba(255,255,255,0.6)", textAlign: "center", letterSpacing: 0.5,
            }}>EDIT</div>
          </div>
          <div style={{ fontSize: 13, color: "rgba(255,255,255,0.3)", lineHeight: 1.6 }}>
            Tap to choose a<br/>photo from gallery
          </div>
        </div>

        {/* Username input */}
        <div style={{
          opacity: visible ? 1 : 0, transform: visible ? "translateY(0)" : "translateY(16px)",
          transition: "all 0.6s cubic-bezier(0.16,1,0.3,1) 0.15s",
        }}>
          <div style={{ fontSize: 11, color: "rgba(255,255,255,0.3)", letterSpacing: 1.5, textTransform: "uppercase", fontWeight: 600, marginBottom: 10 }}>Username</div>
          <div style={{ position: "relative" }}>
            <input
              value={username}
              onChange={e => setUsername(e.target.value)}
              placeholder="Enter a username"
              maxLength={24}
              style={{
                width: "100%", padding: "15px 16px",
                background: "rgba(255,255,255,0.05)",
                border: "1px solid rgba(255,255,255,0.1)",
                borderRadius: 14, color: "#fff",
                fontSize: 16, fontFamily: "'Syne', sans-serif",
                fontWeight: 500, outline: "none",
                boxSizing: "border-box",
              }}
            />
            <div style={{ position: "absolute", right: 14, top: "50%", transform: "translateY(-50%)", fontSize: 12, color: "rgba(255,255,255,0.2)" }}>
              {username.length}/24
            </div>
          </div>
          <div style={{ fontSize: 12, color: "rgba(255,255,255,0.2)", marginTop: 8 }}>Letters, numbers, and underscores only</div>
        </div>
      </div>

      {/* CTA */}
      <div style={{ paddingBottom: 48,
        opacity: visible ? 1 : 0, transform: visible ? "translateY(0)" : "translateY(16px)",
        transition: "all 0.6s cubic-bezier(0.16,1,0.3,1) 0.25s",
      }}>
        <button
          onClick={onNext}
          disabled={username.trim().length < 2}
          style={{
            width: "100%", padding: "17px 0",
            background: username.trim().length >= 2 ? "#fff" : "rgba(255,255,255,0.08)",
            border: "none", borderRadius: 16, cursor: username.trim().length >= 2 ? "pointer" : "not-allowed",
            fontSize: 15, fontWeight: 700,
            color: username.trim().length >= 2 ? "#0A0A0A" : "rgba(255,255,255,0.25)",
            fontFamily: "'Syne', sans-serif",
            transition: "all 0.3s",
          }}
        >
          Enter Offnetic
        </button>
      </div>
    </div>
  );
}

function MainScreen() {
  const [visible, setVisible] = useState(false);
  useEffect(() => { setTimeout(() => setVisible(true), 60); }, []);

  const peers = [
    { name: "Khalid", time: "now", preview: "Nearby · 3m away", online: true },
    { name: "Sara", time: "2m", preview: "File received", online: true },
    { name: "Nour", time: "14m", preview: "Voice note · 0:42", online: false },
    { name: "Faisal", time: "1h", preview: "Call ended · 4:12", online: false },
  ];

  return (
    <div style={{ flex: 1, display: "flex", flexDirection: "column", position: "relative", overflow: "hidden" }}>
      <NoiseBg />

      {/* Header */}
      <div style={{
        padding: "20px 24px 16px",
        opacity: visible ? 1 : 0, transform: visible ? "translateY(0)" : "translateY(-8px)",
        transition: "all 0.5s cubic-bezier(0.16,1,0.3,1)",
        zIndex: 1,
      }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
          <div>
            <div style={{ fontSize: 22, fontWeight: 700, color: "#fff", letterSpacing: -0.5 }}>Offnetic</div>
            <div style={{ fontSize: 12, color: "rgba(255,255,255,0.3)", marginTop: 2, display: "flex", alignItems: "center", gap: 5 }}>
              <div style={{ width: 5, height: 5, borderRadius: "50%", background: "#4ADE80" }}/>
              Discovering nearby
            </div>
          </div>
          <div style={{ display: "flex", gap: 10 }}>
            {/* QR button */}
            <div style={{ width: 38, height: 38, borderRadius: 12, background: "rgba(255,255,255,0.07)", display: "flex", alignItems: "center", justifyContent: "center", cursor: "pointer" }}>
              <svg viewBox="0 0 20 20" fill="none" stroke="rgba(255,255,255,0.7)" strokeWidth="1.5" style={{ width: 18, height: 18 }}>
                <rect x="3" y="3" width="6" height="6" rx="1"/><rect x="11" y="3" width="6" height="6" rx="1"/><rect x="3" y="11" width="6" height="6" rx="1"/>
                <rect x="12" y="12" width="2" height="2"/><rect x="15" y="12" width="2" height="2"/><rect x="12" y="15" width="2" height="2"/><rect x="15" y="15" width="2" height="2"/>
              </svg>
            </div>
            {/* Avatar */}
            <div style={{ width: 38, height: 38, borderRadius: 12, background: "rgba(255,255,255,0.1)", display: "flex", alignItems: "center", justifyContent: "center", fontSize: 14, fontWeight: 700, color: "rgba(255,255,255,0.7)", cursor: "pointer" }}>W</div>
          </div>
        </div>
      </div>

      {/* Nearby ping banner */}
      <div style={{
        margin: "0 16px 12px",
        padding: "12px 16px",
        background: "rgba(255,255,255,0.05)",
        borderRadius: 14, border: "1px solid rgba(255,255,255,0.08)",
        display: "flex", alignItems: "center", gap: 12,
        opacity: visible ? 1 : 0, transform: visible ? "translateY(0)" : "translateY(8px)",
        transition: "all 0.5s cubic-bezier(0.16,1,0.3,1) 0.1s",
        zIndex: 1,
      }}>
        <div style={{ width: 8, height: 8, borderRadius: "50%", background: "#4ADE80", flexShrink: 0, boxShadow: "0 0 8px #4ADE80" }}/>
        <div style={{ fontSize: 13, color: "rgba(255,255,255,0.7)", fontWeight: 500 }}>Khalid is nearby · 3 minutes ago</div>
      </div>

      {/* Chat list */}
      <div style={{ flex: 1, overflow: "auto", padding: "0 0 20px", zIndex: 1 }}>
        {peers.map((p, i) => (
          <div key={i} style={{
            display: "flex", alignItems: "center", gap: 14,
            padding: "12px 24px", cursor: "pointer",
            opacity: visible ? 1 : 0, transform: visible ? "translateX(0)" : "translateX(-12px)",
            transition: `all 0.5s cubic-bezier(0.16,1,0.3,1) ${0.15 + i * 0.06}s`,
          }}
            onMouseEnter={e => e.currentTarget.style.background = "rgba(255,255,255,0.03)"}
            onMouseLeave={e => e.currentTarget.style.background = "transparent"}
          >
            {/* Avatar */}
            <div style={{ position: "relative", flexShrink: 0 }}>
              <div style={{ width: 48, height: 48, borderRadius: 16, background: "rgba(255,255,255,0.08)", display: "flex", alignItems: "center", justifyContent: "center", fontSize: 17, fontWeight: 700, color: "rgba(255,255,255,0.6)" }}>
                {p.name[0]}
              </div>
              {p.online && <div style={{ position: "absolute", bottom: 1, right: 1, width: 9, height: 9, borderRadius: "50%", background: "#4ADE80", border: "2px solid #0A0A0A" }}/>}
            </div>
            {/* Info */}
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline" }}>
                <div style={{ fontSize: 15, fontWeight: 600, color: "#fff", letterSpacing: -0.2 }}>{p.name}</div>
                <div style={{ fontSize: 11, color: "rgba(255,255,255,0.25)", fontWeight: 500 }}>{p.time}</div>
              </div>
              <div style={{ fontSize: 13, color: "rgba(255,255,255,0.3)", marginTop: 2, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{p.preview}</div>
            </div>
          </div>
        ))}
      </div>

      {/* Bottom nav */}
      <div style={{
        display: "flex", justifyContent: "space-around", alignItems: "center",
        padding: "12px 0 28px",
        background: "rgba(10,10,10,0.9)",
        backdropFilter: "blur(20px)",
        borderTop: "1px solid rgba(255,255,255,0.06)",
        zIndex: 2,
      }}>
        {[
          { label: "Chats", active: true, icon: <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.5" style={{ width: 22, height: 22 }}><path d="M2 5a2 2 0 012-2h12a2 2 0 012 2v7a2 2 0 01-2 2H6l-4 4V5z" strokeLinecap="round" strokeLinejoin="round"/></svg> },
          { label: "Nearby", active: false, icon: <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.5" style={{ width: 22, height: 22 }}><circle cx="10" cy="10" r="3"/><path d="M6.343 6.343a6 6 0 000 8.485M13.657 6.343a6 6 0 010 8.485" strokeLinecap="round"/><path d="M3.515 3.515a10 10 0 000 14.142M16.485 3.515a10 10 0 010 14.142" strokeLinecap="round"/></svg> },
          { label: "Settings", active: false, icon: <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.5" style={{ width: 22, height: 22 }}><path d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" strokeLinecap="round"/><circle cx="10" cy="10" r="2" strokeLinecap="round"/></svg> },
        ].map(item => (
          <div key={item.label} style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 4, cursor: "pointer" }}>
            <div style={{ color: item.active ? "#fff" : "rgba(255,255,255,0.25)", transition: "color 0.2s" }}>{item.icon}</div>
            <div style={{ fontSize: 10, color: item.active ? "rgba(255,255,255,0.7)" : "rgba(255,255,255,0.2)", letterSpacing: 0.5, fontWeight: 600 }}>{item.label}</div>
          </div>
        ))}
      </div>
    </div>
  );
}

export default function App() {
  const [screen, setScreen] = useState("splash");
  const [permIndex, setPermIndex] = useState(0);
  const [key, setKey] = useState(0);

  const go = (next) => { setKey(k => k + 1); setScreen(next); };

  const renderScreen = () => {
    switch (screen) {
      case "splash": return <SplashScreen onNext={() => go("perm")} />;
      case "perm": return <PermissionSlide key={`perm-${permIndex}`} slide={permSlides[permIndex]} index={permIndex} total={3} onNext={() => {
        if (permIndex < 2) { setPermIndex(i => i + 1); }
        else go("identity");
      }}/>;
      case "identity": return <IdentityScreen onNext={() => go("profile")} />;
      case "profile": return <ProfileScreen onNext={() => go("main")} />;
      case "main": return <MainScreen />;
      default: return null;
    }
  };

  return (
    <>
      <link href="https://fonts.googleapis.com/css2?family=Syne:wght@400;500;600;700;800&display=swap" rel="stylesheet"/>
      <div style={{ minHeight: "100vh", background: "#050505", display: "flex", alignItems: "center", justifyContent: "center", padding: 40 }}>

        {/* Screen label */}
        <div style={{ position: "fixed", top: 24, left: "50%", transform: "translateX(-50%)", display: "flex", gap: 8 }}>
          {["splash","perm","identity","profile","main"].map(s => (
            <div key={s} style={{
              width: s === screen || (screen === "perm" && s === "perm") ? 24 : 6, height: 6,
              borderRadius: 3, background: (s === screen || (screen === "perm" && s === "perm")) ? "#fff" : "rgba(255,255,255,0.15)",
              transition: "all 0.4s cubic-bezier(0.16,1,0.3,1)",
              cursor: "pointer",
            }} onClick={() => { if (s === "perm") setPermIndex(0); go(s); }}/>
          ))}
        </div>

        <div style={styles.phone}>
          {/* Status bar */}
          <div style={styles.statusBar}>
            <span style={styles.statusTime}>9:41</span>
            <div style={styles.statusIcons}>
              <svg viewBox="0 0 16 12" fill="#fff" style={{ width: 16, height: 12 }}><rect x="0" y="3" width="3" height="9" rx="0.5"/><rect x="4.5" y="2" width="3" height="10" rx="0.5"/><rect x="9" y="0" width="3" height="12" rx="0.5"/><rect x="13.5" y="0" width="2.5" height="12" rx="0.5" opacity="0.3"/></svg>
              <svg viewBox="0 0 16 12" fill="#fff" style={{ width: 16, height: 10 }}><path d="M8 2.4C10.3 2.4 12.4 3.4 13.8 5L15.2 3.6C13.4 1.8 10.8.8 8 .8S2.6 1.8.8 3.6L2.2 5C3.6 3.4 5.7 2.4 8 2.4z" opacity="0.4"/><path d="M8 5.2c1.5 0 2.9.6 3.9 1.6L13.3 5.4C11.9 4 10 3.2 8 3.2S4.1 4 2.7 5.4L4.1 6.8C5.1 5.8 6.5 5.2 8 5.2z"/><circle cx="8" cy="10" r="1.5"/></svg>
              <svg viewBox="0 0 25 12" fill="none" style={{ width: 25, height: 12 }}>
                <rect x="0.5" y="0.5" width="21" height="11" rx="3.5" stroke="#fff" strokeOpacity="0.35"/>
                <rect x="2" y="2" width="16" height="8" rx="2" fill="#fff"/>
                <path d="M23 4v4a2 2 0 000-4z" fill="#fff" fillOpacity="0.4"/>
              </svg>
            </div>
          </div>

          {/* Screen content */}
          <div key={key} style={{ flex: 1, display: "flex", flexDirection: "column", overflow: "hidden" }}>
            {renderScreen()}
          </div>
        </div>

        {/* Nav hint */}
        <div style={{ position: "fixed", bottom: 24, left: "50%", transform: "translateX(-50%)", fontSize: 12, color: "rgba(255,255,255,0.2)", letterSpacing: 0.5 }}>
          Click dots above to jump between screens
        </div>
      </div>
    </>
  );
}
