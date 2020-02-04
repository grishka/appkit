package me.grishka.appkit.example.fragments

import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import me.grishka.appkit.api.PaginatedListCallback
import me.grishka.appkit.example.R
import me.grishka.appkit.example.api.ListPhotosRequest
import me.grishka.appkit.example.api.Photo
import me.grishka.appkit.fragments.BaseRecyclerFragment
import me.grishka.appkit.imageloader.ImageLoaderRecyclerAdapter
import me.grishka.appkit.imageloader.ImageLoaderViewHolder
import me.grishka.appkit.utils.BindableViewHolder
import me.grishka.appkit.utils.V
import me.grishka.appkit.views.UsableRecyclerView

class ExampleLoaderFragment : BaseRecyclerFragment<Photo>(10) {

    private var adapter: PhotoAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTitle("Loader fragment & image loader (also, the title is long and scrolls)")
        if (savedInstanceState != null) {
            onDataLoaded(savedInstanceState.getParcelableArrayList("example:data"))
        } else {
            loadData()
        }
    }

    override fun doLoadData(offset: Int, count: Int) {
        currentRequest = ListPhotosRequest(offset, count)
            .setCallback(PaginatedListCallback(this))
            .exec()
    }

    override fun getAdapter(): RecyclerView.Adapter<*> {
        if (adapter == null)
            adapter = PhotoAdapter()
        return adapter!!
    }

    override fun onCreateLayoutManager(): RecyclerView.LayoutManager {
        return GridLayoutManager(activity, 2)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val pad = V.dp(8f)
        list.setPadding(pad, pad, pad, pad)
        list.clipToPadding = false
        list.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                val pad = V.dp(8f)
                outRect.set(pad, pad, pad, pad)
            }
        })
        (list as UsableRecyclerView).setDrawSelectorOnTop(true)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelableArrayList("example:data", data)
    }

    private inner class PhotoAdapter : UsableRecyclerView.Adapter<PhotoViewHolder>(imgLoader), ImageLoaderRecyclerAdapter {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
            return PhotoViewHolder()
        }

        override fun getItemCount(): Int {
            return data.size
        }

        override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
            holder.bind(data[position])
            super.onBindViewHolder(holder, position)
        }

        override fun getImageCountForItem(position: Int): Int {
            return 1
        }

        override fun getImageURL(position: Int, image: Int): String? {
            return data[position].thumbnailUrl
        }
    }

    private inner class PhotoViewHolder : BindableViewHolder<Photo>(activity, R.layout.loader_item, list), ImageLoaderViewHolder, UsableRecyclerView.Clickable {

        private val title: TextView
        private val image: ImageView

        init {
            title = findViewById(R.id.text)
            image = findViewById(R.id.image)
            itemView.clipToOutline = true
        }

        override fun onBind(item: Photo) {
            title.text = item.title
        }

        override fun setImage(index: Int, bitmap: Bitmap) {
            image.setImageBitmap(bitmap)
        }

        override fun clearImage(index: Int) {
            image.setImageDrawable(ColorDrawable(-0x7f7f80))
        }

        override fun onClick() {
            Toast.makeText(activity, "You clicked item $adapterPosition", Toast.LENGTH_SHORT).show()
        }
    }
}
