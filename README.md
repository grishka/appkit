# AppKit
([not to be confused](https://twitter.com/codinghorror/status/506010907021828096) with Apple's macOS framework)

A boilerplate library/framework/whatever for building Android apps. It's kind of a mishmash of things I need often, and I mostly made this for myself, but someone else might find it useful too. I used it in several projects.

## What's in there

* An image loader
* Some template fragments for common layouts
* A custom fragment back stack that doesn't suck
* An improved RecyclerView
* Some other minor things

### The image loader
Well, it's an image loader. It loads images from the internets.

**Q:** Why did you write your own one when Glide/Picasso/... exists?

**A:** None of those existed back when I wrote my first version of it.

It loads images in multiple threads and caches them both in memory and on disk. It can load images into views like Glide/Picasso, but it can also load them into list views. It's also somewhat extensible with custom protocols. There are two ways of interacting with it:

#### ViewImageLoader
Loads an image into an ImageView or a custom view capable of displaying an image (via an adapter). To load an image into an ImageView:
```java
ViewImageLoader.load(imageView, getResources().getDrawable(R.drawable.placeholder), url);
```
If you'd like to load an image into a custom view, you'll need to implement the `ViewImageLoader.Target` interface and pass that instead of the ImageView:
```java
ViewImageLoader.load(new ViewImageLoader.Target(){
	@Override
    public void setImageBitmap(Bitmap bitmap){
		// Image had loaded. Set it to your view.
	}
	
	@Override
    public void setImageDrawable(Drawable drawable){
		// Set a placeholder drawable.
	}
	
	@Override
    public View getView(){
		// Return your view. The image loader sets a tag on it to keep its internal state.
		return yourView;
	}
}, getResources().getDrawable(R.drawable.placeholder), url);
```
#### ListImageLoaderWrapper
You probably don't want to use this directly as it's already set up correctly in BaseListFragment and BaseRecyclerFragment. This version loads images into list items in a ListView or RecyclerView.

For ListView, your adapter needs to implement the interface `ListImageLoaderAdapter`. For RecyclerView, your adapter needs to extend `UsableRecyclerView.Adapter` and implement `ImageLoaderRecyclerAdapter`, and your view holders (those of them that contain loadable images) need to implement `ImageLoaderViewHolder`. Make sure to call through to `super` in `onBindViewHolder`, but do so **after** your own binding logic.

In both cases, the idea is simple:
* You return how many images are in each item in your list from `getImageCountForItem(int position)`.
* Then, you return the URL for each image for each item from `getImageURL(int position, int image)`. You may return null to skip some images — this makes it easier for complex layouts where some image views in some items aren't visible.
* The image loader starts loading your images. In `ImageLoaderViewHolder`, you set your placeholders in `clearImage(int index)` and set loaded images in `setImage(int index, Bitmap bitmap)`.

The image loader will first load images in the items visible on the screen, then one screen in the direction the user last scrolled, and then one screen in the opposite direction. This ensures that given a sufficiently non-shitty connection, the user won't ever see an image that has not yet loaded. It keeps track of scrolling and would only load images when the user isn't scrolling too fast.

### The fragment templates

These fragments implement some basic behaviors you'll need in the common "way too many screens of lists loaded from the network" kind of app.

To use these, you need to extend your app's theme from `Theme.AppKit` or `Theme.AppKit.Light`.

#### [ToolbarFragment](/appkit/src/main/java/me/grishka/appkit/fragments/ToolbarFragment.java)
A fragment with a toolbar in it. Provide your view by overriding `onCreateContentView()`.

#### [LoaderFragment](/appkit/src/main/java/me/grishka/appkit/fragments/LoaderFragment.java)
An extension of `ToolbarFragment` that loads something from the network. It has three states:
* Loading (initial)
* Content
* Error

You provide the content view the same way. You implement `doLoadData()` to load your data. You can also set `currentRequest` while loading to have your request automatically cancelled in case the user closes the fragment before it completes. When your request has completed, you call either `dataLoaded()` or `onError()` to toggle states.

#### [BaseRecyclerFragment](/appkit/src/main/java/me/grishka/appkit/fragments/BaseRecyclerFragment.java)
An extension of `LoaderFragment` for a RecyclerView that shows a list of something loaded from the network, possibly loading more items as the user scrolls. Also includes a `SwipeRefreshLayout` and the list image loader. There's also an additional state for when the list is empty.

You provide your adapter from `getAdapter()`.

#### Customizing layout
TODO but in short you can provide custom layout resources to these fragments just be sure to include the required views with correct IDs.

### Fragment back stack
Implemented as an activity. Extend your activity from `FragmentStackActivity`. Add fragments from within it via `showFragmentClearingBackStack()`. Navigate from fragments to other fragments via `Nav.go()`. See the example in this repo.

Fragments can customize the appearance of the system bars and handle window insets independently of each other (`setStatusBarColor()`/`setNavigationBarColor()`/`wantsLightStatusBar()`/`wantsLightNavigationBar()`).

It's recommended that you add `android:configChanges="screenSize|orientation"` to your manifest for your activity because this whole "let's recreate activities every time something might have changed, for good measure" is simply stupid. The appkit fragments recreate the toolbar on configuration changes because it refuses to reapply styles without that.

### The improved RecyclerView
`UsableRecyclerView` highlights items and handles taps and long taps on items. Implement `UsableRecyclerView.Clickable` in your view holder to handle clicks; implement `UsableRecyclerView.DisableableClickable` to only handle clicks sometimes; implement `UsableRecyclerView.LongClickable` to handle long clicks.

You can also provide a `SelectorBoundsProvider` to extend the highlight to more than one item. This is useful with my "display items" approach where you slice a complex layout into multiple simple and easily reusable ones.

## Dependencies

* [LiteX](https://github.com/grishka/litex) RecyclerView, SwipeRefreshLayout, ViewPager, collection
* [Jake Wharton's DiskLruCache](https://github.com/JakeWharton/DiskLruCache)
* [Okhttp3](https://github.com/square/okhttp) as an `api` dependency because you probably want the newest version, see [their changelog](https://github.com/square/okhttp/blob/master/docs/changelog_3x.md)

## Usage

```groovy
dependencies {
    implementation 'me.grishka.appkit:appkit:1.0'
}
```