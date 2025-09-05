import sys
import subprocess
import pkg_resources
import pip
import io

from pip._internal import main as pip_main
import os
import os
import sys
import subprocess

import pip
import sys
import os
import io

def get_writable_path():
    # Chaquopy's home dir is writable
    base_dir = os.path.expanduser("~")  
    install_dir = os.path.join(base_dir, "python_packages")
    os.makedirs(install_dir, exist_ok=True)
    return install_dir

def install_package(package_name):
    install_path = get_writable_path()

    if install_path not in sys.path:
        sys.path.insert(0, install_path)

    # Capture pip output
    buffer = io.StringIO()
    sys_stdout_backup = sys.stdout
    sys.stdout = buffer
    try:
        exit_code = pip._internal.main([
            "install",
            package_name,
            "--target", install_path,
            "--no-cache-dir"
        ])
    finally:
        sys.stdout = sys_stdout_backup

    return buffer.getvalue(), exit_code
        
def list_installed_packages():
    """Return installed pip packages as a string."""
    try:
        from pkgutil import iter_modules
        packages = [p.name for p in iter_modules()]
        # packages = sorted([f"{dist.project_name}=={dist.version}" for dist in pkg_resources.working_set])
        return "\n".join(packages) if packages else "No packages installed."
    except Exception as e:
        return f"Error listing packages: {e}"
        
def list_installed_pkg():
    """Return installed pip packages as a string."""
    try:
        packages = pip.main(['list'])
        return "\n".join(packages) if packages else "No packages installed."
    except Exception as e:
        return f"Error listing packages: {e}"
  

def list_all_packages():
    install_path = os.path.join(os.path.expanduser("~"), "python_packages")
    if install_path not in sys.path:
        sys.path.insert(0, install_path)

    # Force pkg_resources to rescan sys.path
    pkg_resources.working_set = pkg_resources.WorkingSet(entries=sys.path)

    packages = sorted(f"{dist.project_name}=={dist.version}" for dist in pkg_resources.working_set)
    return "\n".join(packages) if packages else "No packages installed."

