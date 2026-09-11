import json, re, sys

def no_duplicates(pairs):
    result = {}
    for key, value in pairs:
        assert key not in result, "duplicate key: " + key
        result[key] = value
    return result

schema = json.load(open(sys.argv[1], encoding="utf-8"), object_pairs_hook=no_duplicates)
document = json.load(open(sys.argv[2], encoding="utf-8"), object_pairs_hook=no_duplicates)

def resolve(ref):
    if not ref.startswith("#/"):
        raise AssertionError("external refs are not allowed")
    value = schema
    for part in ref[2:].split("/"):
        value = value[part.replace("~1", "/").replace("~0", "~")]
    return value

def validate(rule, value, path="$", root_rule=None):
    if "$ref" in rule:
        return validate(resolve(rule["$ref"]), value, path)
    if "const" in rule:
        assert value == rule["const"], path
    if "enum" in rule:
        assert value in rule["enum"], path
    kind = rule.get("type")
    if kind == "object":
        assert isinstance(value, dict), path
        for key in rule.get("required", []):
            assert key in value, path + "." + key
        properties = rule.get("properties", {})
        if rule.get("additionalProperties") is False:
            assert set(value).issubset(properties), path
        for key, child in properties.items():
            if key in value:
                validate(child, value[key], path + "." + key)
    elif kind == "array":
        assert isinstance(value, list), path
        if "items" in rule:
            for index, item in enumerate(value):
                validate(rule["items"], item, f"{path}[{index}]")
    elif kind == "string":
        assert isinstance(value, str), path
        assert len(value) >= rule.get("minLength", 0), path
        if "pattern" in rule:
            assert re.search(rule["pattern"], value), path
    elif kind == "integer":
        assert isinstance(value, int) and not isinstance(value, bool), path
        assert value >= rule.get("minimum", value), path

validate(schema, document)
