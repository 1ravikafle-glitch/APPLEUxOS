# Render Quality Levels

## Quality Level Enumeration

| Level | Scale | Label | Description |
|-------|-------|-------|-------------|
| Q100  | 1.00  | 100%  | Full visual quality, all effects enabled |
| Q95   | 0.95  | 95%   | Minor effect reduction |
| Q90   | 0.90  | 90%   | Reduced blur/transparency |
| Q85   | 0.85  | 85%   | Reduced shadows |
| Q80   | 0.80  | 80%   | Reduced resolution |
| Q75   | 0.75  | 75%   | Aggressive effect reduction |
| Q70   | 0.70  | 70%   | Emergency maximum degradation |

## Quality Range

- Normal: 100%
- Maximum adaptive reduction: 70%
- 30% is the hard cap of automatic reduction

## Per-Level Rendering Policy

### Q100 — Full Quality

| Effect               | State  |
|----------------------|--------|
| Blur                 | Full   |
| Transparency         | Full   |
| Shadows              | Full   |
| Internal render scale| 1.0x   |
| Animation complexity | Full   |
| Background effects   | Enabled|

### Q95 — Minor Reduction

| Effect               | State  |
|----------------------|--------|
| Blur                 | Reduced (30%) |
| Transparency         | Reduced (20%) |
| Shadows              | Full   |
| Internal render scale| 1.0x   |
| Animation complexity | Full   |
| Background effects   | Reduced (10%) |

### Q90 — Reduced Effects

| Effect               | State  |
|----------------------|--------|
| Blur                 | Reduced (50%) |
| Transparency         | Reduced (40%) |
| Shadows              | Reduced (30%) |
| Internal render scale| 1.0x   |
| Animation complexity | Reduced (15%) |
| Background effects   | Reduced (30%) |

### Q85 — Further Reduction

| Effect               | State  |
|----------------------|--------|
| Blur                 | Reduced (70%) |
| Transparency         | Reduced (50%) |
| Shadows              | Reduced (50%) |
| Internal render scale| 1.0x   |
| Animation complexity | Reduced (30%) |
| Background effects   | Deferred |

### Q80 — Reduced Resolution

| Effect               | State  |
|----------------------|--------|
| Blur                 | Minimal |
| Transparency         | Reduced (60%) |
| Shadows              | Minimal |
| Internal render scale| 0.85x  |
| Animation complexity | Simplified (40%) |
| Background effects   | Deferred |

### Q75 — Strong Reduction

| Effect               | State  |
|----------------------|--------|
| Blur                 | Disabled |
| Transparency         | Reduced (70%) |
| Shadows              | Disabled |
| Internal render scale| 0.80x  |
| Animation complexity | Simplified (60%) |
| Background effects   | Deferred |

### Q70 — Emergency

| Effect               | State  |
|----------------------|--------|
| Blur                 | Disabled |
| Transparency         | Disabled |
| Shadows              | Disabled |
| Internal render scale| 0.75x  |
| Animation complexity | Minimal (80% simplified) |
| Background effects   | Disabled |

## Internal Render Resolution Mapping

The physical display remains at native resolution (e.g., 1920×1080).
Internal render target resolution scales down where supported.

| Quality | Render Scale | Internal Resolution (1080p panel) |
|---------|-------------|------------------------------------|
| Q100    | 1.00x       | 1920 × 1080                        |
| Q95     | 0.95x       | 1824 × 1026                        |
| Q90     | 0.90x       | 1728 × 972                         |
| Q85     | 0.90x       | 1728 × 972                         |
| Q80     | 0.85x       | 1632 × 918                         |
| Q75     | 0.80x       | 1536 × 864                         |
| Q70     | 0.75x       | 1440 × 810                         |

## Effect Priority

Effects are reduced in this order:

1. Real-time blur (heaviest)
2. Transparency/compositing layers
3. Shadows
4. Animated background complexity
5. Particle/effect complexity
6. Internal render resolution
7. Animation visual complexity

## Visual Identity Preservation

The controller will NOT alter:
- AppleUX color palette
- Core typography
- Primary interaction metaphors
- Essential UI layout structure
- Touch target sizes (minimum 44×44 pt)

The goal is to reduce GPU workload while maintaining the visual language of AppleUX.
