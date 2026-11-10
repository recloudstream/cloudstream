#!/usr/bin/env python3
"""Fail CI if filtered unit/instrumentation tests were accidentally skipped."""
from pathlib import Path
import xml.etree.ElementTree as ET

expected = {
    'ManualProviderRunnerTest': {
        'dispatchesOnlySelectedMethodWithExactInput',
        'forwardsMainPageRequestWithoutReplacingUserData',
        'collectsLoadLinksCallbacksWithoutPlayback',
        'rejectsInvalidInputsBeforeCallingProvider',
        'preservesNotImplementedErrorsForDisplay',
    },
    'ExtensionTransferTest': {
        'transfersExactBytesAndReportsProgress', 'rejectsMismatchedDigest',
        'comparesAnExistingFileDigest',
    },
    'ProviderHostSmokeTest': {
        'launchesOnlyTheLabAndExtensionScreens',
        'loadsCallsAndRemovesAnActualDexProviderExtension',
    },
}
seen = {name: set() for name in expected}
for directory in ['app/build/test-results', 'app/build/outputs/androidTest-results']:
    for report in Path(directory).rglob('*.xml'):
        for case in ET.parse(report).getroot().iter('testcase'):
            suite = case.get('classname', '').rsplit('.', 1)[-1]
            if suite not in expected:
                continue
            assert not any(case.find(tag) is not None for tag in ['failure', 'error', 'skipped']), ET.tostring(case)
            for name in expected[suite]:
                if case.get('name', '').startswith(name):
                    seen[suite].add(name)
for suite, names in expected.items():
    assert names <= seen[suite], f'Missing tests: {suite}: {names - seen[suite]}'
print(f'PASS: {sum(map(len, seen.values()))} focused unit and Android smoke tests executed successfully')
