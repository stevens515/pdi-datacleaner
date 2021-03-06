package org.pentaho.di.profiling.datacleaner;

import java.io.DataInputStream;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.metamodel.MetaModelException;
import org.apache.metamodel.data.AbstractDataSet;
import org.apache.metamodel.data.DataSet;
import org.apache.metamodel.data.DataSetHeader;
import org.apache.metamodel.data.DefaultRow;
import org.apache.metamodel.data.Row;
import org.apache.metamodel.query.SelectItem;
import org.apache.metamodel.schema.Column;
import org.apache.metamodel.util.FileHelper;
import org.pentaho.di.core.exception.KettleEOFException;
import org.pentaho.di.core.exception.KettleValueException;
import org.pentaho.di.core.row.RowMetaInterface;
import org.pentaho.di.core.row.ValueMetaInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class KettleDataSet extends AbstractDataSet implements DataSet {

    private static final Logger logger = LoggerFactory.getLogger(KettleDataSet.class);

    private final DataInputStream inputStream;
    private final RowMetaInterface rowMeta;

    private Object[] row;

    public KettleDataSet(List<Column> columns, DataInputStream inputStream, RowMetaInterface rowMeta) {
        super(columns.stream().map(c -> new SelectItem(c)).collect(Collectors.toList()));
        this.inputStream = inputStream;
        this.rowMeta = rowMeta;
        this.row = null;
    }

    @Override
    public Row getRow() {
        final DataSetHeader header = getHeader();
        final Object[] values = new Object[header.size()];

        if (row != null) {
            for (int i = 0; i < header.size(); i++) {
                final Column column = header.getSelectItem(i).getColumn();
                final int kettleIndex = rowMeta.indexOfValue(column.getName());
                final Object rawValue = row[kettleIndex];
                try {
                    final ValueMetaInterface valueMeta = rowMeta.getValueMeta(kettleIndex);
                    values[i] = valueMeta.convertData(valueMeta, rawValue);
                } catch (KettleValueException e) {
                    throw new MetaModelException(e);
                }
            }
        }

        return new DefaultRow(header, values);
    }

    @Override
    public void close() {
        super.close();
        FileHelper.safeClose(inputStream);
    }

    @Override
    public boolean next() {
        try {
            row = rowMeta.readData(inputStream);
        } catch (KettleEOFException e) {
            logger.debug("No more data to read from input");
            row = null;
        } catch (Exception e) {
            logger.info("Next row not readable, ending stream", e);
            row = null;
        }
        if (row == null) {
            return false;
        }
        return true;
    }
}
