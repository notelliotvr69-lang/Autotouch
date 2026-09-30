import ctypes
import pathlib
import sys

folder = pathlib.Path(sys.argv[1]).resolve()
with __import__('os').add_dll_directory(str(folder)):
    proxy = ctypes.WinDLL(str(folder / 'openvr_api.dll'))
    backend = ctypes.WinDLL(str(folder / 'opencomposite_backend.dll'))
    proxy.VRHeadsetView.restype = ctypes.c_void_p
    assert proxy.VRHeadsetView() is None
    definition = pathlib.Path(__file__).with_name('openvr_api.def').read_text()
    for line in definition.splitlines():
        if '=opencomposite_backend.' not in line:
            continue
        name = line.strip().split('=')[0]
        assert ctypes.cast(getattr(proxy, name), ctypes.c_void_p).value == ctypes.cast(getattr(backend, name), ctypes.c_void_p).value, name
    print('PASS: optional mirror returns null; all 27 original exports forward to the backend.')
