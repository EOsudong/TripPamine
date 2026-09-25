import sys

import orjson
import pydantic


def test_python_version():
    assert (3, 12) <= sys.version_info[:2] < (3, 14)


def test_required_packages():
    assert orjson is not None
    assert pydantic is not None


def test_trippamine_package_import():
    import trippamine_ai

    assert trippamine_ai is not None