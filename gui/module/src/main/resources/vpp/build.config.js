/**
 * This file/module contains all configuration for the build process.
 */
module.exports = {
    /**
     * The `build_dir` folder is where our projects are compiled during
     * development and the `compile_dir` folder is where our app resides once it's
     * completely built.
     */
    build_dir: 'build',
    app_dir: 'app',

    /**
     * This is a collection of file patterns that refer to our app code (the
     * stuff in `src/`). These file paths are used in the configuration of
     * build tasks. `js` is all project javascript, less tests. `ctpl` contains
     * our reusable components' (`src/common`) template HTML files, while
     * `atpl` contains the same, but for our app's code. `html` is just our
     * main HTML file, `less` is our main stylesheet, and `unit` contains our
     * app's unit tests.
     */
    app_files: {
        controller_js: ['controllers/*.js'],
        services_js: ['services/*.js'],
        root_js: ['main.js', 'vpp.module.js'],

        templates: ['views/*.tpl.html'],
    },

    assets_files: {
        css: ['assets/css/*.css'],
        js: ['assets/js/*.js'],
        fonts: ['assets/fonts/*.*'],
        images: ['assets/images/*.*'],
        lang: ['assets/data/*.json']
    },

    /**
     * This is a collection of files used during testing only.
     */


    /**
     * This is the same as `app_files`, except it contains patterns that
     * reference vendor code (`vendor/`) that we need to place into the build
     * process somewhere. While the `app_files` property ensures all
     * standardized files are collected for compilation, it is the user's job
     * to ensure non-standardized (i.e. vendor-related) files are handled
     * appropriately in `vendor_files.js`.
     *
     * The `vendor_files.js` property holds files to be automatically
     * concatenated and minified with our project source files.
     *
     * The `vendor_files.css` property holds any CSS files to be automatically
     * included in our app.
     *
     * The `vendor_files.assets` property holds any assets to be copied along
     * with our app's assets. This structure is flattened, so it is not
     * recommended that you use wildcards.
     */
    vendor_files: {
        js: [
            'angular-material/angular-material.min.js',
            'angular-animate/angular-animate.min.js',
            'angular-aria/angular-aria.min.js',
            'angular-smart-table/dist/smart-table.js',
            'angular-ui-grid/ui-grid.min.js'
        ],
        css: [
            'angular-material/angular-material.min.css',
            'roboto-fontface/css/roboto-fontface.css'
        ],
        fonts: ['roboto-fontface/fonts/*.*']
    }

};
