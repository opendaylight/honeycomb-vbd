/**
 * Created by dakuzma on 19. 2. 2016.
 */

var gulp = require('gulp'),
    del = require('del'),
    gutil = require('gulp-util'),
    concat = require('gulp-concat'),
    runSequence = require('run-sequence'),
    install = require("gulp-install");

var config = require( './build.config.js' );

/**
 * Task for cleaning build directory
 */
gulp.task('clean', function() {
    // You can use multiple globbing patterns as you would with `gulp.src`
    return del([config.build_dir]);
});

/**
 * Copy assets
 */
gulp.task('copyCss', function () {
    return gulp.src(config.assets_files.css)
        .pipe(gulp.dest(config.build_dir + '/assets/css'));
});

gulp.task('copyAssetsFonts', function () {
    gutil.log(gutil.colors.cyan('INFO :: copying Assets fonts files'));
    return gulp.src(config.assets_files.fonts)
        .pipe(gulp.dest(config.build_dir + '/assets/fonts'));
});

gulp.task('copyAssetsJs', function () {
    gutil.log(gutil.colors.cyan('INFO :: copying Assets JS files'));
    return gulp.src(config.assets_files.js)
        .pipe(gulp.dest(config.build_dir + '/assets/js'));
});

gulp.task('copyAssetsLang', function () {
    gutil.log(gutil.colors.cyan('INFO :: copying Assets Lang files'));
    return gulp.src(config.assets_files.lang)
        .pipe(gulp.dest(config.build_dir + '/assets/data'));
});

gulp.task('copyAssetsImages', function () {
    gutil.log(gutil.colors.cyan('INFO :: copying Assets Image files'));
    return gulp.src(config.assets_files.images)
        .pipe(gulp.dest(config.build_dir + '/assets/images'));
});

/**
 * Copy app files
 */
gulp.task('copyTemplates', function () {
    gutil.log(gutil.colors.cyan('INFO :: copying APP Template files'));
    // Copy html
    return gulp.src(config.app_files.templates)
        .pipe(gulp.dest(config.build_dir + '/views'));
});

gulp.task('copyControllerJs', function () {
    gutil.log(gutil.colors.cyan('INFO :: copying APP Controller JS files'));
    return gulp.src(config.app_files.controller_js)
        .pipe(gulp.dest(config.build_dir + '/controllers'));
});

gulp.task('copyServiceJs', function () {
    gutil.log(gutil.colors.cyan('INFO :: copying APP Service JS files'));
    return gulp.src(config.app_files.services_js)
        .pipe(gulp.dest(config.build_dir + '/services'));
});

gulp.task('copyRootJs', function () {
    gutil.log(gutil.colors.cyan('INFO :: copying APP Root JS files'));
    return gulp.src(config.app_files.root_js)
        .pipe(gulp.dest(config.build_dir));
});

/**
  * Copy vendor files
 */
gulp.task('copyVendorCss', function () {
    gutil.log(gutil.colors.cyan('INFO :: copying VENDOR css'));
    return gulp.src(config.vendor_files.css, { cwd : 'node_modules/**' })
        .pipe(gulp.dest(config.build_dir + '/vendor'))
});

gulp.task('copyVendorFonts', function () {
    gutil.log(gutil.colors.cyan('INFO :: copying VENDOR fonts'));
    return gulp.src(config.vendor_files.fonts, { cwd : 'node_modules/**' })
        .pipe(gulp.dest(config.build_dir + '/vendor'))
});

gulp.task('copyVendorJs', function () {
    gutil.log(gutil.colors.cyan('INFO :: copying VENDOR js files'));
    return gulp.src(config.vendor_files.js, { cwd : 'node_modules/**' })
        .pipe(gulp.dest(config.build_dir + '/vendor'))
});


/**
 * Copy task aggregated
 */
gulp.task('copy', function() {
    runSequence([
        'copyCss',
        'copyAssetsFonts',
        'copyAssetsJs',
        'copyAssetsLang',
        'copyAssetsImages',
        'copyTemplates',
        'copyControllerJs',
        'copyServiceJs',
        'copyRootJs',
        'copyVendorCss',
        'copyVendorFonts'
    ], 'copyVendorJs');
});

/**
 * Build task
 */
gulp.task('build', function(){
    runSequence('clean', 'copy');
});
