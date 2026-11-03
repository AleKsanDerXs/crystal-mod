"""Run with python tools/check_stall_resources.py; no Minecraft runtime required."""
import json
from pathlib import Path
root = Path(__file__).resolve().parents[1] / "src/main/resources"
def load(path):
    return json.loads((root / path).read_text(encoding="utf-8"))
state = load("assets/crystalmod/blockstates/market_stall.json")
model_id = state["variants"][""]["model"].split(":", 1)[1]
model = load("assets/crystalmod/models/" + model_id + ".json")
for element in model["elements"]:
    assert all(0 <= a < b <= 16 for a, b in zip(element["from"], element["to"]))
    for face in element["faces"].values():
        assert face["texture"][1:] in model["textures"]
recipe = load("data/crystalmod/recipe/market_stall.json")
drop = load("data/crystalmod/loot_table/blocks/market_stall.json")
assert recipe["result"]["id"] == drop["pools"][0]["entries"][0]["name"]
assert recipe["result"]["count"] == 1
for lang in ("ru_ru", "en_us"):
    strings = load("assets/crystalmod/lang/" + lang + ".json")
    assert strings["block.crystalmod.market_stall"]
    assert strings["crystalmod.stall.preview"].count("%s") == 2
print("Stall resource checks passed")

# Public use must not expose the stock container to Carry On pickup.
blacklist = load("data/carryon/tags/block/block_blacklist.json")
assert blacklist.get("replace") is False
assert "crystalmod:market_stall" in blacklist["values"]
# Architectural regression check: the stall must remain event-driven.
java = root.parent / "java/ru/alex/crystalmod"
for name in ("MarketStallBlock.java", "MarketStallEntity.java", "StallClaimsCompat.java"):
    source = (java / name).read_text(encoding="utf-8")
    for forbidden in ("getTicker(", "scheduleTick(", "ServerTickEvent", "ScheduledExecutor"):
        assert forbidden not in source, (name, forbidden)
print("Carry On and no-ticker checks passed")
