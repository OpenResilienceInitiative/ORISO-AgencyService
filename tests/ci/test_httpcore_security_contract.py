from pathlib import Path
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[2]
MAVEN = {"m": "http://maven.apache.org/POM/4.0.0"}


def test_httpcore_modules_are_pinned_to_the_fixed_release():
    pom = ET.parse(ROOT / "pom.xml").getroot()
    properties = pom.find("m:properties", MAVEN)
    assert properties is not None
    assert properties.findtext("m:httpcore5.version", namespaces=MAVEN) == "5.4.3"

    managed_versions = {
        (
            dependency.findtext("m:groupId", namespaces=MAVEN),
            dependency.findtext("m:artifactId", namespaces=MAVEN),
        ): dependency.findtext("m:version", namespaces=MAVEN)
        for dependency in pom.findall(
            "m:dependencyManagement/m:dependencies/m:dependency", MAVEN
        )
    }
    expected_version = "${httpcore5.version}"
    assert managed_versions[("org.apache.httpcomponents.core5", "httpcore5")] == (
        expected_version
    )
    assert managed_versions[("org.apache.httpcomponents.core5", "httpcore5-h2")] == (
        expected_version
    )
