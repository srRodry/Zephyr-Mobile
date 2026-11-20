cmake_minimum_required(VERSION 3.14)

if(NOT ${CMAKE_ANDROID_ARCH_ABI} STREQUAL arm64-v8a)
	message(FATAL_ERROR "Unsupported target architecture: ${CMAKE_ANDROID_ARCH_ABI}. Please make an issue on the repo!")
endif()

project(adrenotools LANGUAGES CXX C)

set(GEN_INSTALL_TARGET OFF CACHE BOOL "")

add_subdirectory(lib/linkernsbypass)

set(LIB_SOURCES src/bcenabler.cpp
                src/driver.cpp
                include/adrenotools/bcenabler.h
                include/adrenotools/driver.h
                include/adrenotools/priv.h)

add_library(adrenotools ${LIB_SOURCES})

target_include_directories(adrenotools PUBLIC include)
target_include_directories(adrenotools PRIVATE .)
target_compile_options(adrenotools PRIVATE -Wall -Wextra)
target_link_libraries(adrenotools android linkernsbypass)

add_subdirectory(src/hook)
set(ADRENOTOOLS_HOOK_PREFIX "" CACHE STRING "Prefix for packaged driver hook libraries")
foreach(hook IN ITEMS hook_impl main_hook file_redirect_hook gsl_alloc_hook)
    set_target_properties(${hook} PROPERTIES OUTPUT_NAME "${ADRENOTOOLS_HOOK_PREFIX}${hook}")
endforeach()
foreach(target IN ITEMS adrenotools hook_impl)
    target_compile_definitions(${target} PRIVATE ADRENOTOOLS_HOOK_PREFIX="${ADRENOTOOLS_HOOK_PREFIX}")
endforeach()
if (${BUILD_SHARED_LIBS})
    target_link_options(adrenotools PRIVATE "-Wl,-s,--exclude-libs,liblinkernsbypass.a")
endif()
if (${GEN_INSTALL_TARGET})
  configure_file(adrenotools.pc.in adrenotools.pc @ONLY)
  install(FILES ${CMAKE_BINARY_DIR}/adrenotools.pc DESTINATION lib/pkgconfig)
  install(TARGETS adrenotools main_hook file_redirect_hook gsl_alloc_hook hook_impl)
  install(DIRECTORY include/
          DESTINATION include
          FILES_MATCHING PATTERN "*.h*")
endif()
