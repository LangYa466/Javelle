def package_name:
  . | capture("(?:^|/)node_modules/(?<name>(?:@[^/]+/)?[^/]+)$").name;

{
  schemaVersion: 1,
  sourceLockfile: "config/frontend/package-lock.json",
  components: [
    .packages
    | to_entries[]
    | select(.key != "")
    | {
        path: .key,
        name: (.key | package_name),
        version: .value.version,
        integrity: .value.integrity,
        license: .value.license,
        source: .value.resolved
      }
  ] | sort_by(.path)
}
