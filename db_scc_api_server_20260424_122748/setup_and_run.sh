#!/bin/bash

##############################################################################
# DB_Scc API Server - Setup and Run Script
# 
# This script:
# 1. Checks for Python 3
# 2. Creates and activates a virtual environment
# 3. Installs all required dependencies
# 4. Verifies that all local data files are present
# 5. Starts the API server on port 5001
#
# Usage: ./setup_and_run.sh [--venv-only|--install-only|--help]
##############################################################################

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Script directory
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
VENV_DIR="${SCRIPT_DIR}/.venv"
PYTHON_CMD="python3"

# Local data files that the server needs
DATA_FILES=(
    "lancs_fac.json"
    "delay-codes.json"
    "weather.json"
    "busLive.xml"
    "naptan.xml"
    "nptg.xml"
    "vms"
    "toc-full"
    "lancs_station_depart"
    "bplan.txt"
    "bus.json"
    "CORPUSExtract.json"
    "SMARTExtract.json"
)

##############################################################################
# FUNCTIONS
##############################################################################

print_header() {
    echo -e "\n${BLUE}========================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}========================================${NC}\n"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

print_info() {
    echo -e "${BLUE}ℹ $1${NC}"
}

check_python() {
    print_header "Checking Python Installation"
    
    if ! command -v ${PYTHON_CMD} &> /dev/null; then
        print_error "Python 3 is not installed"
        echo "Please install Python 3.7 or higher from https://www.python.org/"
        exit 1
    fi
    
    PYTHON_VERSION=$(${PYTHON_CMD} --version 2>&1 | awk '{print $2}')
    print_success "Found ${PYTHON_CMD} version ${PYTHON_VERSION}"
}

create_venv() {
    print_header "Setting Up Virtual Environment"
    
    if [ -d "${VENV_DIR}" ]; then
        print_warning "Virtual environment already exists at ${VENV_DIR}"
        echo "To recreate it, remove the directory with: rm -rf ${VENV_DIR}"
    else
        print_info "Creating virtual environment..."
        ${PYTHON_CMD} -m venv "${VENV_DIR}"
        print_success "Virtual environment created"
    fi
}

activate_venv() {
    print_info "Activating virtual environment..."
    source "${VENV_DIR}/bin/activate"
    print_success "Virtual environment activated"
}

install_dependencies() {
    print_header "Installing Dependencies"
    
    if [ ! -f "${SCRIPT_DIR}/requirements.txt" ]; then
        print_error "requirements.txt not found in ${SCRIPT_DIR}"
        exit 1
    fi
    
    print_info "Installing packages from requirements.txt..."
    pip install --upgrade pip setuptools wheel > /dev/null 2>&1
    pip install -r "${SCRIPT_DIR}/requirements.txt"
    print_success "All dependencies installed"
}

check_data_files() {
    print_header "Verifying Data Files"
    
    local missing_files=0
    
    for file in "${DATA_FILES[@]}"; do
        if [ -e "${SCRIPT_DIR}/${file}" ]; then
            print_success "Found: ${file}"
        else
            print_warning "Missing: ${file}"
            ((missing_files++))
        fi
    done
    
    if [ ${missing_files} -gt 0 ]; then
        print_warning "${missing_files} data file(s) missing"
        echo "The server will attempt to fetch missing data from remote URLs."
        echo "To restore missing files, restore from backup or download from:"
        echo "  - transport.scc.lancs.ac.uk"
    else
        print_success "All data files present!"
    fi
}

create_data_package() {
    print_header "Creating Data Package"
    
    local package_dir="${SCRIPT_DIR}/data_files"
    local zip_file="${SCRIPT_DIR}/db_scc_data.zip"
    
    # Create temporary directory for packaging
    mkdir -p "${package_dir}"
    
    print_info "Copying data files..."
    for file in "${DATA_FILES[@]}"; do
        if [ -e "${SCRIPT_DIR}/${file}" ]; then
            cp -r "${SCRIPT_DIR}/${file}" "${package_dir}/" 2>/dev/null && \
                print_success "Copied: ${file}" || \
                print_warning "Could not copy: ${file}"
        fi
    done
    
    # Create zip archive
    print_info "Creating zip archive..."
    cd "${SCRIPT_DIR}"
    zip -r -q "${zip_file}" "data_files/" -x "data_files/.cache/*" 2>/dev/null && \
        print_success "Data package created: ${zip_file}" || \
        print_warning "Could not create zip file"
    
    # Cleanup temporary directory
    rm -rf "${package_dir}"
}

verify_server_script() {
    print_header "Verifying Server Script"
    
    if [ ! -f "${SCRIPT_DIR}/api_server.py" ]; then
        print_error "api_server.py not found in ${SCRIPT_DIR}"
        exit 1
    fi
    print_success "api_server.py found"
}

start_server() {
    print_header "Starting DB_Scc API Server"
    
    print_info "Server will run on http://localhost:5001"
    print_info "Press Ctrl+C to stop the server"
    echo ""
    
    cd "${SCRIPT_DIR}"
    ${PYTHON_CMD} api_server.py
}

show_help() {
    cat << EOF
${BLUE}DB_Scc API Server - Setup and Run Script${NC}

${GREEN}Usage:${NC}
    ./setup_and_run.sh [OPTION]

${GREEN}Options:${NC}
    --venv-only      Only create/verify virtual environment
    --install-only   Only install dependencies (requires existing venv)
    --data-package   Create a zip file with all data files
    --check-files    Check which data files are present
    --help           Show this help message

${GREEN}Examples:${NC}
    ./setup_and_run.sh                 # Full setup and start server
    ./setup_and_run.sh --venv-only     # Only create venv
    ./setup_and_run.sh --install-only  # Only install packages
    ./setup_and_run.sh --data-package  # Create deployable data package

${GREEN}Requirements:${NC}
    - Python 3.7 or higher
    - pip package manager
    - ~200MB disk space for virtual environment and packages

${GREEN}Default Port:${NC}
    5001 (http://localhost:5001)

${GREEN}Available Endpoints:${NC}
    /health                  - Health check
    /api/endpoints          - List all endpoints
    /api/route              - Shortest route calculation
    /api/departures         - Train departure board
    /api/weather            - Current weather
    /api/places/*           - Place search endpoints
    ... and many more (see /api/endpoints)

EOF
}

##############################################################################
# MAIN EXECUTION
##############################################################################

# Parse command line arguments
case "${1:-}" in
    --venv-only)
        check_python
        create_venv
        activate_venv
        print_success "Virtual environment is ready!"
        echo "To use it, run: source .venv/bin/activate"
        exit 0
        ;;
    --install-only)
        if [ ! -d "${VENV_DIR}" ]; then
            print_error "Virtual environment not found. Run without --install-only first."
            exit 1
        fi
        activate_venv
        install_dependencies
        print_success "Dependencies installed!"
        exit 0
        ;;
    --data-package)
        check_data_files
        create_data_package
        exit 0
        ;;
    --check-files)
        check_data_files
        exit 0
        ;;
    --help)
        show_help
        exit 0
        ;;
    "")
        # Full setup and run
        check_python
        create_venv
        activate_venv
        install_dependencies
        verify_server_script
        check_data_files
        start_server
        ;;
    *)
        print_error "Unknown option: $1"
        echo "Use --help for usage information"
        exit 1
        ;;
esac
